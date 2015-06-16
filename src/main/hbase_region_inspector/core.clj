(ns hbase-region-inspector.core
  (:require [clojure.pprint :refer [pprint]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response content-type resource-response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [compojure.core :refer [defroutes GET routes wrap-routes]]
            [compojure.route :as route]
            [hiccup.core :as h]
            [selmer.parser :refer [render-file]]
            [hbase-region-inspector.hbase :as hbase]
            [hbase-region-inspector.util :as util])
  (:gen-class))

;;; ZooKeeper quorum we point to
(defonce zookeeper (atom "localhost"))

;;; Cache the result of previous inspection
(defonce cached (atom {:updated-at nil :regions []}))

;;; Inspection interval
(def update-interval 10000)

(let [palette (atom {})]
  (defn color-for-table [table]
    "Function to assign colors for tables"
    (or (@palette table)
        (let [new-color (util/color-pair)]
          (swap! palette assoc table new-color)
          new-color))))

(defn format-val
  "String formatter for region properties"
  [type val]
  (let [mb #(format "%d MB" (long %))
        kb #(format "%d KB" (long %))
        rate #(format "%.2f" (double %))]
    (case type
      :store-file-size-mb         (mb val)
      :store-uncompressed-size-mb (mb val)
      :store-file-index-size-mb   (mb val)
      :memstore-size-mb           (mb val)
      :requests-rate              (rate val)
      :read-requests-rate         (rate val)
      :write-requests-rate        (rate val)
      :root-index-size-kb         (kb val)
      :bloom-size-kb              (kb val)
      :total-index-size-kb        (kb val)
      val)))

(defn build-html
  "Builds a small HTML snippet for each region to be used in bootstrap popover"
  [props]
  (let [{:keys [table encoded-name]} props
        rest (dissoc props :table :encoded-name :name)]
    (h/html
      [:h3 table " " [:small encoded-name]]
      [:table {:class "table table-condensed table-striped"}
       (map #(identity [:tr
                        [:td (first %)]
                        [:td (apply format-val %)]]) rest)])))

(defn regions
  "Collects the information of online regions"
  [zk]
  (hbase/admin-let
    [admin zk]
    (hbase/collect-region-info admin)))

(defn regions-by-servers
  "Generates output for /server_regions.json. Regions grouped by their servers."
  [metric sort table]
  (let [all-regions (filter (complement :meta?) (:regions @cached))
        ;; Filter regions by table name
        filtered-regions (filter #(or (empty? table) (= (:table %) table)) all-regions)
        filtered (seq filtered-regions)
        filter-error (boolean (and (seq table) (not filtered)))
        all-regions (if filtered filtered-regions all-regions)

        ;; Find the list of tables for all regions
        all-tables (group-by :table all-regions)
        ;; Sort the tables in descending order by the sum of the given metric
        all-tables (keys (reverse (sort-by
                                    #(reduce + (map metric (last %)))
                                    all-tables)))
        ;; Group by server, sort the pairs, build a list of maps with :name and :regions
        grouped (map #(zipmap [:name :regions] %)
                     (sort-by first util/compare-server-names
                              (group-by :server all-regions)))
        ;; Function to sort the regions in the descending order
        sort-fn (if (= sort :metric)
                  (fn [regions] (reverse (sort-by metric regions)))
                  (fn [regions] (sort-by #(identity [(.indexOf all-tables (:table %))
                                                     (- (metric %))]) regions)))
        ;; Sort the regions in each server
        grouped (map #(update-in % [:regions] sort-fn) grouped)
        ;; Find the local sum of the metric of each region
        grouped (map #(assoc % :sum (reduce + (map metric (:regions %))))
                     grouped)
        ;; Find the max of the sums
        group-max (if (not-empty grouped)
                    (apply max (map :sum grouped))
                    nil)]
    ;; Build the result list
    {:servers (map #(assoc % :max group-max) grouped)
     :error filter-error}))

(defn regions-by-tables
  "Generates output for /table_regions.json. Regions grouped by their tables."
  [metric]
  (let [metric (or metric :store-file-size-mb)
        ;; Exclude hbase:meta table
        all-regions (filter (complement :meta?) (:regions @cached))
        ;; Group regions by table name
        grouped (group-by :table all-regions)
        ;; Calculate the sum for each group
        grouped-sum (into {}
                          (for [[table regions] grouped]
                            [table (reduce #(+ %1 (metric %2)) 0 regions)]))
        ;; List of maps with table-level sums
        list-with-sum (map #(assoc (zipmap [:name :regions] %)
                                   :sum (format-val metric (grouped-sum (first %))))
                           grouped)
        ;; Sort regions in each partition by start key
        locally-sorted (map #(update-in % [:regions]
                                        (fn [regions] (sort-by :start-key regions)))
                            list-with-sum)
        ;; Sort the list by table name
        sorted (sort-by :name locally-sorted)]
    sorted))

(defn update-regions!
  "Collects region info from HBase and store it in @cached"
  []
  (util/info "Updating regions")
  (let [old-regions (into {} (for [region (:regions @cached)]
                               [(:name region) region]))
        new-regions (regions @zookeeper)
        prev-time (:updated-at @cached)
        now (System/currentTimeMillis)
        interval (- now (or prev-time now))
        diff-fn (if (zero? interval)
                  (fn [& _] 0)
                  (fn [region metric]
                    (let [name (:name region)
                          new-val (metric region)
                          old-val (or (get-in old-regions [name metric]) new-val)]
                      (/ (- new-val old-val) interval 0.001))))
        new-regions (map #(assoc
                            %
                            :requests-rate (diff-fn % :requests)
                            :write-requests-rate (diff-fn % :write-requests)
                            :read-requests-rate (diff-fn % :read-requests))
                         new-regions)
        ;; Build HTML for popover and assign color
        new-regions (map #(assoc %
                                 :html (build-html %)
                                 :color (color-for-table (:table %)))
                         new-regions)]
    (reset! cached {:updated-at now
                    :regions new-regions})))

(defn start-periodic-updater!
  "Starts a thread that periodically runs update-regions!"
  []
  (future
    (loop []
      (Thread/sleep update-interval)
      (try
        (#'update-regions!)
        (catch Exception e
          (util/error e)))
      (recur))))

;;; Compojure route for web app
(defroutes app-routes
  (GET "/" []
       (render-file "public/index.html" {:zookeeper @zookeeper
                                         :updated-at (:updated-at @cached)}))
       ;; (content-type (resource-response "index.html" {:root "public"})
       ;;               "text/html"))
  (route/resources "/" {:root "public"}))

;;; Compojure route for API
(defroutes api-routes
  (GET "/regions.json" _ (response @cached))
  (GET "/server_regions.json"
       {{:keys [sort table metric]
         :or {sort "metric" table "" metric "store-file-size-mb"}} :params}
       (response
         (regions-by-servers (keyword metric) (keyword sort) table)))
  (GET "/table_regions.json" {{metric :metric} :params}
       (response
         (regions-by-tables (keyword metric)))))

(def app
  "Combined route of app routes and api routes"
  (routes (-> app-routes
              (wrap-routes wrap-defaults site-defaults))
          (-> api-routes
              (wrap-routes wrap-defaults api-defaults)
              wrap-json-response)
          (route/not-found "404")))

(defn- bootstrap [zk port bg]
  ;; Make sure that we can connect to the given ZooKeeper quorum before
  ;; starting background process
  (reset! zookeeper zk)
  (update-regions!)
  ;; Start background process
  (when bg
    (util/info "Start periodic update process")
    (start-periodic-updater!))
  ;; Start web server
  (util/info (format "Starting web server: http://%s:%d"
                     (util/local-ip-address) port))
  (run-jetty app {:port port}))

(defn exit [message]
  (println message)
  (println "usage: java -jar hbase-region-inspector.jar QUORUM PORT")
  (System/exit 1))

(defn -main [& args]
  (when (not= 2 (count args)) (exit "invalid number of arguments"))
  (try
    (let [[zk port] args
          port (Integer/parseInt port)]
      (bootstrap zk port true))
    (catch NumberFormatException e (exit "invalid port"))))


(ns hbase-region-inspector.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response content-type resource-response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [compojure.core :refer [defroutes GET PUT routes wrap-routes]]
            [compojure.route :as route]
            [hiccup.core :as h]
            [selmer.parser :refer [render-file]]
            [hbase-region-inspector.hbase :as hbase]
            [hbase-region-inspector.util :as util]
            [hbase-region-inspector.config :as config])
  (:gen-class))

(defonce ^{:doc "Application configuration"} config (atom {}))
(defonce ^{:doc "Whether we should allow region relocation or not"} read-only? (atom true))
(defonce ^{:doc "Whether we should include system regions or not"} with-system? (atom true))
(defonce ^{:doc "Number of updates"} ticks (atom 0))
(defonce ^{:doc "Cached result of the previous inspection"} cached
  (atom {:updated-at   nil
         :has-locality false
         :regions      []
         :servers      {}
         :tables       {}
         :response     {}}))
(defonce ^{:doc "Inspection interval"} update-interval (atom 10))

(let [long-fmt   #(str/replace (str (long %)) #"\B(?=(\d{3})+(?!\d))" ",")
      mb         #(str (long-fmt %) " MB")
      kb         #(str (long-fmt %) " KB")
      rate       #(if (> % 10) (long-fmt %) (format "%.2f" (double %)))
      count-rate #(if %2
                    (format "%s (%s/sec)" (long-fmt %1) (rate %2))
                    (long-fmt %1))]
  (defn format-val
    "String formatter for region properties"
    [type val & [props]]
    (case type
      :start-key                ["Start key"  (util/byte-array->str val)]
      :end-key                  ["End key"    (util/byte-array->str val)]
      :regions                  ["Regions"    (long-fmt val)]
      :store-files              ["Storefiles" (long-fmt val)]
      :store-file-size-mb       ["Data size"
                                 (if-let [uncmp (:store-uncompressed-size-mb props)]
                                   (if (pos? uncmp)
                                     (format "%s / %s (%.2f%%)"
                                             (mb val) (mb uncmp)
                                             (double (/ val uncmp 0.01)))
                                     (str (mb val) " / " (mb uncmp)))
                                   (mb val))]
      :locality                 ["Locality"     (str (int val) " %")]
      :store-file-index-size-mb ["Index"        (mb val)]
      :memstore-size-mb         ["Memstore"     (mb val)]
      :requests-rate            ["Requests/sec" (long-fmt val)]
      :requests                 ["Requests"     (count-rate val (:requests-rate props))]
      :read-requests            ["Reads"        (count-rate val (:read-requests-rate props))]
      :write-requests           ["Writes"       (count-rate val (:write-requests-rate props))]
      :root-index-size-kb       ["Root index"   (kb val)]
      :bloom-size-kb            ["Bloom filter" (kb val)]
      :total-index-size-kb      ["Total index"  (kb val)]
      :compaction               ["Compaction"   (str/join " / " (map long-fmt val))]
      :used-heap-mb             ["Used heap"    (mb val)]
      :max-heap-mb              ["Max heap"     (mb val)]
      [(util/keyword->str (str type))
       (if (instance? Number val) (rate val) val)])))

(defn build-popover
  "Builds a small HTML snippet for an entity"
  [title keys props]
  (let [has-locality (:has-locality @cached)
        {:keys [table encoded-name]} props]
    (h/html
      title
      [:table {:class "table table-condensed table-striped"}
       [:tbody
        (map #(let [[k v] (format-val % (% props) props)]
                [:tr [:th {:class "col-xs-2"} k] [:td v]])
             (filter #(and (% props)
                           ;; Ignore locality if not available
                           (or has-locality (not= % :locality))) keys))]])))

(defn build-region-popover
  "Builds a small HTML snippet for each region to be used in bootstrap popover"
  [props]
  (let [{:keys [table encoded-name]} props]
    (build-popover
      [:h3 table " " [:small encoded-name]]
      [:server
       :start-key :end-key
       :store-file-size-mb
       :store-files
       :locality
       :memstore-size-mb
       :requests
       :read-requests
       :write-requests
       :compaction]
      props)))

(defn build-server-popover
  "Builds a small HTML snippet for each server to be used in bootstrap popover"
  [props]
  (build-popover
    [:h3 (:name props)]
    [:regions
     :store-files
     :store-file-size-mb
     :locality
     :requests-rate
     :used-heap-mb
     :max-heap-mb]
    props))

(defn build-table-popover
  "Builds a small HTML snippet for each table to be used in bootstrap popover"
  [props]
  (build-popover
    [:h3 (:name props)]
    [:regions
     :store-files
     :store-file-size-mb
     :locality
     :requests
     :read-requests
     :write-requests
     :used-heap-mb
     :max-heap-mb]
    props))

(defn collect-info
  "Collects information from HBase."
  [config]
  (hbase/with-admin
    [admin config]
    (hbase/collect-info admin)))

(defn region-location?
  "Finds the host region server of the region"
  [admin encoded-name]
  (let [all-regions (hbase/collect-region-info admin)]
    (when-let [the-region
               (first (filter #(= (:encoded-name %) encoded-name)
                              all-regions))]
      (:server the-region))))

(defn byte-buffers->str
  "Returns an updated map with start-key and end-key as strings"
  [region]
  (reduce (fn [region prop]
            (update region prop util/byte-array->str))
          region
          [:start-key :end-key]))

(def ^:private cumulative-metrics #{:requests :write-requests :read-requests})

(defn- system?
  "Checks if the region is a system region."
  [region]
  (or (:meta? region)
      (if-let [table (:table region)]
        (or (str/starts-with? table "hbase:")
            (#{".META." "-ROOT-"} table)))))

(defn- filter-system-tables
  "System regions are not shown if --no-system option is provided. They are
  also excluded regardless of the flag in tabs where cumulative counts
  are shown as their values often dwarf those of the other regions."
  [with-system? metric regions]
  (filter #(or (not (system? %))
               (and with-system?
                    (not (cumulative-metrics metric))))
          regions))

(defn calculate-locality
  "Calculates the aggregated locality of the regions."
  [regions]
  (let [[loc tot] (reduce #(let [{loc :local-size-mb tot :store-file-size-mb
                                  :or {loc 0 tot 0}} %2]
                             (doall (map + %1 [loc tot])))
                          [0 0] regions)
        accum {:local-size-mb loc}]
    (if (pos? tot)
      (assoc accum :locality (* 100 (/ loc tot)))
      accum)))

(defn regions-by-servers
  "Generates output for /server_regions.json. Regions grouped by their servers."
  [{:keys [regions servers metric sort tables with-system?]
    :or   {tables nil with-system? true}}]
  (let [all-regions (filter-system-tables with-system? metric regions)

        ;; Sort the tables in descending order by the sum of the given metric
        all-tables (keys (sort-by
                           #(reduce - 0 (map metric (val %)))
                           (group-by :table all-regions)))

        ;; Tables to show
        visible-tables (set (if (seq tables) tables all-tables))

        ;; Filter regions by table name
        visible-regions (map byte-buffers->str
                             (filter #(visible-tables (:table %)) all-regions))

        ;; Group by server, sort the pairs, build a list of maps with :name and :regions
        grouped (map #(zipmap [:name :regions] %)
                     (sort-by key util/compare-server-names
                              (merge (zipmap (keys servers) (repeat []))
                                     (group-by :server visible-regions))))
        ;; Function to sort the regions in the descending order
        score-fn #(vector (- (metric %))
                          (.indexOf all-tables (:table %)))
        sort-fn (if (= sort :metric)
                  (fn [regions] (sort-by score-fn regions))
                  (fn [regions] (sort-by (comp vec reverse score-fn) regions)))
        ;; Sort the regions in each server
        grouped (pmap #(update % :regions sort-fn) grouped)
        ;; Find the local sum of the metric of each region
        grouped (map #(assoc % :sum (reduce + (filter pos? (map metric (:regions %)))))
                     grouped)
        ;; Find the max of the sums
        group-max (if (seq grouped)
                    (apply max (map :sum grouped))
                    nil)]
    ;; Build the result list
    {:servers (map #(assoc %
                           :max group-max
                           :props (servers (:name %))) grouped)
     :tables (or all-tables [])}))

(defn regions-by-tables
  "Generates output for /table_regions.json. Regions grouped by their tables."
  [{:keys [regions table-summary metric sort tables with-system?]
    :or   {tables nil with-system? true}}]
  (let [all-regions (filter-system-tables with-system? metric regions)

        ;; Sort the tables in their names
        all-tables (->> all-regions (map :table) (apply sorted-set) vec)

        ;; Regions to show
        visible-regions (if (seq tables)
                          (let [table-set (set tables)]
                            (filter #(table-set (:table %)) all-regions))
                          all-regions)

        ;; Sort the regions
        sorted-regions (if (= sort :metric)
                         (reverse (sort-by metric visible-regions))
                         (sort-by :start-key hbase/bytes-comp visible-regions))
        ;; ByteBuffer -> strings
        visible-regions (map byte-buffers->str sorted-regions)
        ;; Group regions by table name
        grouped (group-by :table visible-regions)
        ;; Calculate the sum for each group
        grouped-sum (into {}
                          (for [[table regions] grouped]
                            [table (reduce #(+ %1 (metric %2)) 0 regions)]))
        ;; List of maps with table-level sums
        list-with-sum (map #(assoc (zipmap [:name :regions] %)
                                   :sum (grouped-sum (key %)))
                           grouped)
        ;; Sort the list by table name
        sorted (sort-by :name list-with-sum)
        ;; Sorted with human-readable sum
        sorted (map #(assoc %
                            :sumh (last (format-val metric (:sum %)))
                            :props (get table-summary (:name %))) sorted)]
    {:all-tables all-tables
     :tables     sorted}))

(defn group-by-tables
  "Aggregate table statistics from regions"
  [regions]
  (reduce (fn [summary region]
            (let [table (:table region)
                  sofar (summary table)
                  data (select-keys region [:requests
                                            :requests-rate
                                            :read-requests
                                            :read-requests-rate
                                            :write-requests
                                            :write-requests-rate
                                            :store-file-size-mb
                                            :store-uncompressed-size-mb
                                            :local-size-mb
                                            :store-files
                                            :used-heap-mb
                                            :max-heap-mb])
                  with-count (assoc data :regions 1)
                  new (merge-with + sofar with-count)]
              (assoc summary table (assoc new :name table))))
          {} regions))

(defn post-process-tablewise-data
  "Attaches locality and HTML popover to each table information"
  [grouped]
  (into {}
        (for [[table props] grouped]
          [table
           (let [with-locality (merge props (calculate-locality [props]))]
             ;; Popover should be generated after locality is calculated
             (assoc with-locality
                    :html
                    (build-table-popover with-locality)))])))

(defn update-regions!
  "Collects region info from HBase and store it in @cached"
  []
  (util/debug "Updating regions")
  (let [old-regions (into {} (for [region (:regions @cached)]
                               [(:name region) region]))
        {new-regions  :regions
         servers      :servers
         has-locality :has-locality} (util/elapsed-time "collect-info"
                                                        (collect-info @config))
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
        new-regions (pmap #(assoc
                             %
                             :requests-rate       (diff-fn % :requests)
                             :write-requests-rate (diff-fn % :write-requests)
                             :read-requests-rate  (diff-fn % :read-requests)
                             :compaction          ((juxt :compacted-kvs :total-compacting-kvs) %))
                          new-regions)
        new-regions (pmap #(assoc % :html (build-region-popover %)) new-regions)
        group-by-server (group-by :server new-regions)
        servers (into {} (for [[k v] servers]
                           (let [v (merge v (calculate-locality (group-by-server k)))]
                             ;; Build popover *after* we calculate the locality
                             [k (assoc v :html (build-server-popover v))])))]
    (swap! ticks inc)
    (reset! cached {:updated-at   now
                    :regions      new-regions
                    :servers      servers
                    :has-locality has-locality
                    :tables       (post-process-tablewise-data
                                    (group-by-tables new-regions))
                    :response     {}})))

(defn start-periodic-updater!
  "Starts a thread that periodically runs update-regions!"
  []
  (future
    (loop []
      (Thread/sleep (* @update-interval 1000))
      (try
        (#'update-regions!)
        (catch Exception e
          (util/error e)))
      (recur))))

(defn json-response
  "Returns JSON response for the given data"
  [data]
  (-> data json/generate-string response))

(defmacro cached-json-response
  "Evaluates forms and builds JSON response with the result of it. The response
  is cached by the keys."
  [[& keys] & forms]
  (let [keys (vec keys)]
    `(if-let [body# (get-in @cached [:response ~keys])]
       (do
         (util/debug (format "Returning cached JSON [%s]" (str ~keys)))
         (response body#))
       (let [json# (json/generate-string (assoc (do ~@forms) :ticks @ticks))]
         (swap! cached #(assoc-in % [:response ~keys] json#))
         (response json#)))))

(defn strip-zookeeper-quorum
  "Returns a compact representation of the ZooKeeper quorum"
  [zk]
  (->> (str/split zk #",")
       (map #(first (str/split % #"[:\.]")))
       (str/join ",")))

;;; Compojure route for web app
(defroutes app-routes
  (GET "/" {remote :remote-addr}
       (util/debug (format "/ [%s]" remote))
       (render-file "public/index.html" {:zookeeper (strip-zookeeper-quorum (:zookeeper @config))
                                         :interval @update-interval
                                         :rs-port (hbase/rs-info-port @config)
                                         :admin (not @read-only?)
                                         :has-locality (:has-locality @cached)
                                         :updated-at (:updated-at @cached)}))
       ;; (content-type (resource-response "index.html" {:root "public"})
       ;;               "text/html"))
  (route/resources "/" {:root "public"}))

;;; Compojure route for API
(defroutes api-routes
  (GET "/server_regions.json"
       {{:keys [sort metric]
         :or {sort "metric" metric "store-file-size-mb"}
         :as params} :params
        remote :remote-addr}
       (util/debug (format "server_regions.json [%s]" remote))
       (let [tables (get params "tables[]" [])
             tables (if (instance? String tables) [tables] tables)]
         (cached-json-response ["server" sort metric tables]
           (regions-by-servers (merge (select-keys   @cached  [:regions :servers])
                                      {:metric       (keyword metric)
                                       :sort         (keyword sort)
                                       :tables       tables
                                       :with-system? @with-system?})))))
  (GET "/table_regions.json"
       {{:keys [sort metric]
         :or {sort "metric" metric "store-file-size-mb"}
         :as params} :params
        remote :remote-addr}
       (util/debug (format "table_regions.json [%s]" remote))
       (let [tables (get params "tables[]" [])
             tables (if (instance? String tables) [tables] tables)]
         (cached-json-response ["table" sort metric tables]
           (regions-by-tables {:regions       (:regions @cached)
                               :table-summary (:tables @cached)
                               :metric        (keyword metric)
                               :sort          (keyword sort)
                               :tables        tables
                               :with-system?  @with-system?}))))
  (PUT "/move_region" {{:keys [src dest region]} :params remote :remote-addr}
       (util/debug (format "move_region [%s]" remote))
       (when @read-only?
         (throw (Exception. "Read-only mode. Not allowed.")))
       (hbase/with-admin
         [admin @config]
         (.move admin (.getBytes ^String region) (.getBytes ^String dest))
         (loop [tries 20
                message (format "Moving %s from %s to %s" region src dest)]
           (util/info message)
           (when (> tries 0)
             (Thread/sleep 250)
             (let [loc (region-location? admin region)]
               (if (nil? loc)
                 (recur (dec tries) "Region is not online")
                 (if (not= loc dest)
                   (recur (dec tries) "Region not yet moved")
                   (util/info "Region is moved")))))))
       (update-regions!)
       (json-response {})))

(defn wrap-exception
  "Ring middleware that catches the exception and turn it into 500 response"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :body (.getMessage e)}))))

(def app
  "Combined route of app routes and api routes"
  (routes (-> app-routes
              (wrap-routes wrap-defaults site-defaults))
          (-> api-routes
              (wrap-routes wrap-defaults api-defaults)

              ;; Response middlewares
              wrap-gzip

              ;; Outermost shell
              wrap-exception)
          (route/not-found "404")))

(defn- bootstrap
  "Starts the application"
  [conf port bg]
  ;; Make sure that we can connect to the cluster before
  ;; starting background process
  (reset! config conf)
  (update-regions!)
  ;; Start background process
  (when bg
    (util/info "Start periodic update process")
    (start-periodic-updater!))
  ;; Start web server
  (util/info "Starting web server:")
  (doseq [ip (util/local-ip-addresses)]
    (util/info (format "  http://%s:%d" ip port)))
  (run-jetty app {:port port}))

(defn- exit
  "Prints the message and terminates the program"
  [message code]
  (if (seq message)
    (println message))
  (println
    (str/join
      "\n"
      ["usage: hbase-region-inspector [OPTIONS] ┌ QUORUM[/ZKPORT] ┐ PORT [INTERVAL]"
       "                                        └ CONFIG_FILE     ┘"
       "  Options"
       "   --admin       Enable drag-and-drop interface"
       "   --no-system   Hide system tables"
       "   --help        Show this message"]))
  (System/exit code))

(defn -main [& args]
  (let [{opts true args false} (group-by #(str/starts-with? % "-") args)
        opts (set (map #(keyword (str/replace % #"^-*" "")) opts))]
    (if-let [unknowns (seq (set/difference opts #{:help :admin :no-system}))]
      (exit (str "unknown options: " unknowns) 1))
    (if (:help opts) (exit "" 0))
    (reset! read-only?   (not (contains? opts :admin)))
    (reset! with-system? (not (contains? opts :no-system)))
    (when-not (<= 2 (count args) 3) (exit "invalid number of arguments" 1))
    (try
      (let [[spec port interval] args
            conf (config/parse spec)
            port (Integer/parseInt port)]
        (if interval
          (reset! update-interval (Integer/parseInt interval)))
        (bootstrap conf port true))
      (catch NumberFormatException e (exit "invalid port" 1)))))


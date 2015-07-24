(ns hbase-region-inspector.util
  (:require [clojure.string :as str]))

(defn compare-server-names
  "Comparator function for server names. Pads numbers with zeros"
  [left right]
  (let [fmt   (fn [s] (str/replace s #"[0-9]+" #(format "%04d" (Long/parseLong %))))
        left  (fmt left)
        right (fmt right)]
    (.compareTo left right)))

(defn rand-range
  "Returns a random number between min and max"
  [min max]
  (+ min (rand (- max min))))

(defn local-ip-addresses
  "Returns the first local IPv4 address"
  []
  (let [addrs (->> (java.net.NetworkInterface/getNetworkInterfaces)
                   enumeration-seq
                   (filter #(.isUp %))
                   (remove #(.isLoopback %))
                   (mapcat #(enumeration-seq (.getInetAddresses %)))
                   (filter #(instance? java.net.Inet4Address %))
                   (map #(.getHostAddress %)))]
    (if (empty? addrs) ["127.0.0.1"] addrs)))

(defn keyword->str
  [keyword]
  (-> keyword
      clojure.core/str
      (str/replace #"[:-]" " ")
      str/trim
      str/capitalize))

;; Use hand-crafted logger functions instead of tools.logging
(defn- log [type message]
  (println (format "%s: %s: %s" (java.util.Date.) type message)))
(defn info [message] (log "INFO" message))
(defn debug [message]
  (if (System/getenv "DEBUG")
    (log "DEBUG" message)))
(defn error [message] (log "ERROR" message))

(defmacro elapsed-time
  [name & body]
  `(let [started-at# (System/currentTimeMillis)]
     (try
       ~@body
       (finally
         (~debug (format "%s (Elapsed: %dms)"
                         ~name
                         (- (System/currentTimeMillis) started-at#)))))))

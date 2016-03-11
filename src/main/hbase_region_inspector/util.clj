(ns hbase-region-inspector.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn compare-server-names
  "Comparator function for server names. Pads numbers with zeros"
  [left right]
  (let [fmt   (fn [s] (str/replace s #"[0-9]+" #(format "%04d" (Long/parseLong %))))
        left  (fmt left)
        right (fmt right)]
    (.compareTo ^String left right)))

(defn local-ip-addresses
  "Returns local IPv4 addresses"
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
  "Converts keyword to capitalized string"
  [keyword]
  (-> keyword
      clojure.core/str
      (str/replace #"[:-]" " ")
      str/trim
      str/capitalize))

(defn locate-file
  "Tries to find the absolute path of the given path"
  [basedir relpath]
  (let [file (io/file relpath)]
    (cond
      (.isAbsolute file) relpath
      (.exists file) (.getAbsolutePath file)
      :else
      (let [file (io/file basedir relpath)]
        (if (.exists file)
          (.getAbsolutePath file)
          relpath)))))

;;; Use hand-crafted logger functions instead of tools.logging
(defn- log [type & message]
  (println (format "%s: %s: %s" (java.util.Date.) type (apply str message))))
(defn debug [& message]
  (if (System/getenv "DEBUG")
    (apply log "DEBUG" message)))
(defn info [& message] (apply log "INFO" message))
(defn warn [& message] (apply log "WARN" message))
(defn error [& message] (apply log "ERROR" message))

(defmacro elapsed-time
  [name & body]
  `(let [started-at# (System/currentTimeMillis)]
     (try
       ~@body
       (finally
         (~debug (format "%s (Elapsed: %dms)"
                         ~name
                         (- (System/currentTimeMillis) started-at#)))))))

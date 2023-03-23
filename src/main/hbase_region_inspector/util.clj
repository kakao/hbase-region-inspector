(ns hbase-region-inspector.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(let [hex-chars ^chars (into-array Character/TYPE
                            (mapcat #(map char (range (int (key %))
                                                      (inc (int (val %)))))
                                    {\0 \9 \A \F}))]
  (defn byte-array->str
    "Returns the string representation of a bytes array"
    [^bytes buf]
    (let [len (count buf)
          sb  (StringBuilder.)]
      (dotimes [idx len]
        (let [ch  (aget buf idx)
              chi (bit-and 0xff ch)]
          (if (and (<= 32 chi 126) (not= 92 chi))
            (.append sb (char ch))
            (do
              (.append sb "\\x")
              (.append sb (aget hex-chars (quot chi 0x10)))
              (.append sb (aget hex-chars (mod  chi 0x10)))))))
      (str sb))))

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
                   (filter #(.isUp ^java.net.NetworkInterface %))
                   (remove #(.isLoopback ^java.net.NetworkInterface %))
                   (mapcat #(enumeration-seq (.getInetAddresses ^java.net.NetworkInterface %)))
                   (filter #(instance? java.net.Inet4Address %))
                   (map #(.getHostAddress ^java.net.InetAddress %)))]
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

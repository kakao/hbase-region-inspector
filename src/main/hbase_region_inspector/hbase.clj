(ns hbase-region-inspector.hbase
  (:require [clojure.string :as str]
            [hbase-region-inspector.hbase.impl :as hbase-impl])
  (:import org.apache.hadoop.hbase.client.HBaseAdmin
           org.apache.hadoop.hbase.HBaseConfiguration
           org.apache.hadoop.hbase.util.Bytes))

;; https://support.pivotal.io/hc/en-us/articles/200933006-Hbase-application-hangs-indefinitely-connecting-to-zookeeper
(defn- connect-admin [zk]
  (let [[quorum port] (str/split zk #"/")
        port (or port 2181)]
    (HBaseAdmin.
      (doto (HBaseConfiguration/create)
        (.set "hbase.zookeeper.quorum" quorum)
        (.setInt "hbase.zookeeper.property.clientPort" port)
        (.setInt "hbase.client.retries.number" 2)
        (.setInt "hbase.regions.slop" 0)
        (.setInt "zookeeper.recovery.retry" 2)))))

(defmacro admin-let
  [[name zk] & body]
  `(let [admin# (~connect-admin ~zk)
         ~name admin#]
     (try (doall ~@body) (finally (.close admin#)))))

(defn byte-buffer->str
  [buf]
  (Bytes/toStringBinary buf))

(def collect-region-info hbase-impl/collect-region-info)
(def region-map hbase-impl/region-map)
(def bytes-comp Bytes/BYTES_COMPARATOR)


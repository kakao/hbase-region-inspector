(ns hbase-region-inspector.hbase
  (:require [hbase-region-inspector.hbase.impl :as hbase-impl])
  (:import org.apache.hadoop.hbase.client.HBaseAdmin
           org.apache.hadoop.hbase.HBaseConfiguration
           org.apache.hadoop.hbase.util.Bytes
           java.nio.ByteBuffer))

;; https://support.pivotal.io/hc/en-us/articles/200933006-Hbase-application-hangs-indefinitely-connecting-to-zookeeper
(defn- connect-admin [zk]
  (HBaseAdmin.
    (doto (HBaseConfiguration/create)
      (.set "hbase.zookeeper.quorum" zk)
      (.setInt "hbase.client.retries.number" 2)
      (.setInt "hbase.regions.slop" 0)
      (.setInt "zookeeper.recovery.retry" 2))))

(defmacro admin-let
  [[name zk] & body]
  `(let [admin# (~connect-admin ~zk)
         ~name admin#]
     (try (doall ~@body) (finally (.close admin#)))))

(def collect-region-info hbase-impl/collect-region-info)
(def region-map hbase-impl/region-map)


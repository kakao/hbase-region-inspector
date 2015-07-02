(ns hbase-region-inspector.hbase
  (:require [clojure.string :as str]
            [hbase-region-inspector.hbase.impl :as hbase-impl])
  (:import org.apache.hadoop.hbase.client.HBaseAdmin
           org.apache.hadoop.hbase.HBaseConfiguration
           org.apache.hadoop.hbase.util.Bytes))

(defn- server-load->map
  "Transforms ServerLoad object into clojure map"
  [load]
  {:max-heap-mb        (.getMaxHeapMB load)
   :used-heap-mb       (.getUsedHeapMB load)
   :regions            (.getNumberOfRegions load)
   :requests-rate      (.getNumberOfRequests load)
   :store-files        (.getStorefiles load)
   :store-file-size-mb (.getStorefileSizeInMB load)})

(defn- collect-server-info
  "Collects server statistics"
  [cluster-status]
  (let [server-names (.getServers cluster-status)
        server-loads (map #(.getLoad cluster-status %) server-names)]
    (into
      {}
      (map (fn [[name load]]
             (let [name (str name)]
               [name
                (assoc (server-load->map load) :name name)]))
           (zipmap server-names server-loads)))))

(def collect-region-info hbase-impl/collect-region-info)

(defn collect-info
  "Collects server and region statistics"
  [admin]
  (let [cluster-status (.getClusterStatus admin)]
    {:servers (doall (collect-server-info cluster-status))
     :regions (doall (collect-region-info admin cluster-status))}))

(def bytes-comp Bytes/BYTES_COMPARATOR)

(defn byte-buffer->str
  "Returns the string representation of a bytes array"
  [buf]
  (Bytes/toStringBinary buf))

;; https://support.pivotal.io/hc/en-us/articles/200933006-Hbase-application-hangs-indefinitely-connecting-to-zookeeper
(defn- connect-admin [zk]
  (let [[quorum port] (str/split zk #"/")
        port (or port 2181)]
    (HBaseAdmin.
      (doto (HBaseConfiguration/create)
        (.set "hbase.zookeeper.quorum" quorum)
        (.setInt "hbase.zookeeper.property.clientPort" port)
        (.setInt "hbase.client.retries.number" 1)
        (.setInt "hbase.regions.slop" 0)
        (.setInt "zookeeper.recovery.retry" 1)))))

(defmacro admin-let
  [[name zk] & body]
  `(let [admin# (~connect-admin ~zk)
         ~name admin#]
     (try (doall ~@body) (finally (.close admin#)))))

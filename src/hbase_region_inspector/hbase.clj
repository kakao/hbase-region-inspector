(ns hbase-region-inspector.hbase
  (:require [clojure.pprint :refer [pprint]]
            [clojure.set :as set])
  (:import org.apache.hadoop.hbase.client.HBaseAdmin
           org.apache.hadoop.hbase.HBaseConfiguration
           org.apache.hadoop.hbase.util.Bytes
           java.nio.ByteBuffer))

;; https://support.pivotal.io/hc/en-us/articles/200933006-Hbase-application-hangs-indefinitely-connecting-to-zookeeper
(defn- connect-admin [zk]
  (HBaseAdmin.
    (doto (HBaseConfiguration/create)
      (.set "hbase.zookeeper.quorum" zk)
      (.setInt "hbase.client.retries.number" 0)
      (.setInt "hbase.regions.slop" 0)
      (.setInt "zookeeper.recovery.retry" 0))))

(defmacro admin-let
  [[name zk] & body]
  `(let [admin# (~connect-admin ~zk)
         ~name admin#]
     (try (doall ~@body) (finally (.close admin#)))))

(defn- info->map
  "Builds map from HRegionInfo"
  [info]
  {:encoded-name (.getEncodedName info)
   :table        (str (.getTable info))
   :start-key    (Bytes/toStringBinary (.getStartKey info))
   :end-key      (Bytes/toStringBinary (.getEndKey info))
   :meta?        (.isMetaRegion info)})

(defn- load->map
  "Builds map from RegionLoad"
  [load]
  {:compacted-kvs              (.getCurrentCompactedKVs load)
   :memstore-size-mb           (.getMemStoreSizeMB load)
   :read-requests              (.getReadRequestsCount load)
   :requests                   (.getRequestsCount load)
   :root-index-size-kb         (.getRootIndexSizeKB load)
   :store-file-index-size-mb   (.getStorefileIndexSizeMB load)
   :store-files                (.getStorefiles load)
   :store-file-size-mb         (.getStorefileSizeMB load)
   :stores                     (.getStores load)
   :store-uncompressed-size-mb (.getStoreUncompressedSizeMB load)
   :total-compacting-kvs       (.getTotalCompactingKVs load)
   :bloom-size-kb              (.getTotalStaticBloomSizeKB load)
   :total-index-size-kb        (.getTotalStaticIndexSizeKB load)
   :write-requests             (.getWriteRequestsCount load)})

;; Get HRegionInfo from HBaseAdmin
(defn- online-regions
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [admin server-name]
  (into
    {}
    (for [info (.getOnlineRegions admin server-name)]
      [(ByteBuffer/wrap (.getRegionName info)) (info->map info)])))

;; Get RegionLoad from ClusterStatus
(defn- region-loads
  "Extracts region information from ClusterStatus.getLoad"
  [cluster-status server-name]
  (into
    {}
    (for [[region-name load]
          (->> server-name
               (.getLoad cluster-status)
               (.getRegionsLoad))]
      [(ByteBuffer/wrap region-name) (load->map load)])))

(defn collect-region-info
  "Returns the region information as a list of maps"
  [admin]
  (let [cluster-status (.getClusterStatus admin)
        aggregate-fn #(let [base-map {:server (.getServerName %)}
                            ;; We have two different sources of info
                            from-info (future (online-regions admin %))
                            from-load (future (region-loads cluster-status %))
                            ;; And we do not want to build maps with partial info
                            common-keys (set/intersection (set (keys @from-info))
                                                          (set (keys @from-load)))]
                        (merge-with
                          (fn [& rs] (apply merge base-map rs))
                          (select-keys @from-info common-keys)
                          (select-keys @from-load common-keys)))
        server-names (.getServers cluster-status)
        aggregated (map aggregate-fn server-names)]
    (for [server-regions aggregated
          [k v] server-regions]
      (assoc v :name (Bytes/toStringBinary (.array k))))))


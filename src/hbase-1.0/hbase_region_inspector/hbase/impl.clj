(ns hbase-region-inspector.hbase.impl
  (:require [clojure.set :as set]
            [hbase-region-inspector.util :as util]
            [hbase-region-inspector.hbase.base :as base])
  (:import [org.apache.hadoop.hbase
            util.Bytes client.Admin
            HRegionInfo RegionLoad ClusterStatus ServerName ServerLoad]
           java.nio.ByteBuffer))

;; http://archive.cloudera.com/cdh5/cdh/5/hbase-0.98.6-cdh5.3.3/apidocs/index.html

(defn info->map
  "Builds map from HRegionInfo"
  [^HRegionInfo info]
  (assoc (base/info->map info)
         :table (str (.getTable info))))

(defn load->map
  "Builds map from RegionLoad"
  [^RegionLoad load]
  (let [base {:compacted-kvs              (.getCurrentCompactedKVs load)
              :memstore-size-mb           (.getMemStoreSizeMB load)
              :read-requests              (.getReadRequestsCount load)
              :requests                   (.getRequestsCount load)
              :root-index-size-kb         (.getRootIndexSizeKB load)
              :store-file-index-size-mb   (.getStorefileIndexSizeMB load)
              :store-files                (.getStorefiles load)
              :store-file-size-mb         (.getStorefileSizeMB load)
              :stores                     (.getStores load)
              ; :store-uncompressed-size-mb
              :total-compacting-kvs       (.getTotalCompactingKVs load)
              :bloom-size-kb              (.getTotalStaticBloomSizeKB load)
              :total-index-size-kb        (.getTotalStaticIndexSizeKB load)
              :write-requests             (.getWriteRequestsCount load)}
        loc  (.getDataLocality load)]
    (assoc base
      :store-uncompressed-size-mb (.getStoreUncompressedSizeMB load)
      :locality (* 100 loc)
      :local-size-mb (* loc (:store-file-size-mb base)))))

(defn server-load->map
  "Transforms ServerLoad object into clojure map"
  [^ServerLoad load]
  (assoc
    {:max-heap-mb        (.getMaxHeapMB load)
     :used-heap-mb       (.getUsedHeapMB load)
     :regions            (.getNumberOfRegions load)
     :requests-rate      (.getNumberOfRequests load)
     :store-files        (.getStorefiles load)
     :store-file-size-mb (.getStorefileSizeInMB load)}
    :store-uncompressed-size-mb
    (some->> load
             str
             (re-find #"storefileUncompressedSizeMB=([0-9]+)")
             last
             Integer/parseInt)))

(defn- online-regions
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [^Admin admin server-name]
  (into
    {}
    (for [^HRegionInfo info (.getOnlineRegions admin server-name)]
      [(ByteBuffer/wrap (.getRegionName info)) (info->map info)])))

;; Get RegionLoad from ClusterStatus
(defn- region-loads
  "Extracts region information from ClusterStatus.getLoad"
  [^ClusterStatus cluster-status
   ^ServerName    server-name]
  (into
    {}
    (for [[region-name load]
          (->> server-name
               (.getLoad cluster-status) ; HServerLoad
               (.getRegionsLoad))]       ; byte[] -> RegionLoad
      [(ByteBuffer/wrap region-name) (load->map load)])))

(defn- aggregate-two-sources
  "Merges the maps of region information from the two sources of information.
  Regions that are not found on the both sources are discarded."
  [cluster-status admin ^ServerName server-name]
  (let [base-map {:server (.getServerName server-name)}
        ;; We have two different sources of info
        from-info (future (online-regions admin server-name))
        from-load (future (region-loads cluster-status server-name))
        ;; And we do not want to build maps with partial info
        common-keys (set/intersection (set (keys @from-info))
                                      (set (keys @from-load)))]
    (merge-with
      (fn [& rs] (apply merge base-map rs))
      (select-keys @from-info common-keys)
      (select-keys @from-load common-keys))))

(defn collect-region-info
  "Returns the region information as a list of maps"
  [admin ^ClusterStatus cluster-status]
  (let [server-names (.getServers cluster-status)
        aggregated (pmap (partial aggregate-two-sources cluster-status admin)
                         server-names)]
    (for [region->info aggregated
          [k v] region->info]
      (assoc v :name (util/byte-array->str (.array ^ByteBuffer k))))))

(ns hbase-region-inspector.hbase.impl
  (:require [clojure.set :as set]
            [hbase-region-inspector.util :as util]
            [hbase-region-inspector.hbase.base :as base])
  (:import org.apache.hadoop.hbase.client.HConnectionManager
           org.apache.hadoop.hbase.client.HTable
           org.apache.hadoop.hbase.util.Bytes
           org.apache.hadoop.hbase.HServerLoad
           org.apache.hadoop.hbase.HServerLoad$RegionLoad
           java.nio.ByteBuffer))

;; http://archive.cloudera.com/cdh4/cdh/4/hbase-0.94.15-cdh4.7.1/apidocs/index.html

(defn info->map
  "Builds map from HRegionInfo"
  [info]
  (assoc (base/info->map info)
         :table (Bytes/toString (.getTableName info))))

(defn load->map
  "Builds map from RegionLoad"
  [^HServerLoad$RegionLoad load]
  (assoc {:compacted-kvs              (.getCurrentCompactedKVs load)
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
         :store-uncompressed-size-mb
         (->> load
              str
              (re-find #"storefileUncompressedSizeMB=([0-9]+)")
              last
              Integer/parseInt)))

(defn server-load->map
  "Transforms ServerLoad object into clojure map"
  [^HServerLoad load]
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

;; Get HRegionInfo from HBaseAdmin
(defn- region-locations
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [admin]
  (let [conn (HConnectionManager/createConnection (.getConfiguration admin))]
    (try
      (let [table-descs (.listTables admin)
            table-names (map #(.getName %) table-descs)
            ;; Region location maps (<HRegionInfo,ServerName>)
            loc-futures (doall ; To start futures right away
                          (map #(future
                                  (.getRegionLocations
                                    (cast HTable (.getTable conn %))))
                               table-names))]
        (reduce merge {} (map deref loc-futures)))
      (finally (.close conn)))))

;; Get HRegionInfo from HBaseAdmin
(defn- online-regions
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [admin]
  (let [info->server (region-locations admin)
        ;; Reverse key-value pairs and group by servers
        server->infos (reduce #(update %1 (first %2) conj (last %2))
                              {}
                              (for [[info server] info->server]
                                [(.getServerName server) info]))]
    ;; Server -> region name -> region info
    (into {} (for [[server-name region-infos] server->infos]
               [server-name
                (into {} (for [info region-infos]
                           [(ByteBuffer/wrap (.getRegionName info))
                            (info->map info)]))]))))


;; Get RegionLoad from ClusterStatus
(defn- region-loads
  "Extracts region information from ClusterStatus.getLoad"
  [cluster-status server-name]
  (into
    {}
    (for [[region-name load]
          (->> server-name
               (.getLoad cluster-status) ; HServerLoad
               (.getRegionsLoad))]       ; byte[] -> RegionLoad
      [(ByteBuffer/wrap region-name) (load->map load)])))

(defn collect-region-info
  "Returns the region information as a list of maps"
  [admin cluster-status]
  (let [server->regions (future (online-regions admin))
        aggregate-fn (fn [server-name]
                       (let [name-str (.getServerName server-name)
                             base-map {:server name-str}
                             ;; We have two different sources of info
                             from-info (future (get @server->regions name-str))
                             from-load (future (region-loads cluster-status server-name))
                             ;; And we do not want to build maps with partial info
                             common-keys (set/intersection (set (keys @from-info))
                                                           (set (keys @from-load)))]
                         (merge-with
                           (fn [& rs] (apply merge base-map rs))
                           (select-keys @from-info common-keys)
                           (select-keys @from-load common-keys))))
        server-names (.getServers cluster-status)
        aggregated (map aggregate-fn server-names)]
    (for [region->info aggregated
          [k v] region->info]
      (assoc v :name (util/byte-array->str (.array k))))))

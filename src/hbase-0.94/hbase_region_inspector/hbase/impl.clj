(ns hbase-region-inspector.hbase.impl
  (:require [clojure.set :as set])
  (:import org.apache.hadoop.hbase.client.HConnectionManager
           org.apache.hadoop.hbase.client.HTable
           org.apache.hadoop.hbase.util.Bytes
           java.nio.ByteBuffer))

;; http://archive.cloudera.com/cdh4/cdh/4/hbase-0.94.15-cdh4.7.1/apidocs/index.html

(defn info->map
  "Builds map from HRegionInfo"
  [info]
  {:encoded-name (.getEncodedName info)
   :table        (Bytes/toString (.getTableName info))
   :start-key    (.getStartKey info)
   :end-key      (.getEndKey info)
   :meta?        (.isMetaRegion info)})

(defn load->map
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
   :store-uncompressed-size-mb (->> load
                                    str
                                    (re-find #"storefileUncompressedSizeMB=([0-9]+)")
                                    last
                                    Integer/parseInt)
   :total-compacting-kvs       (.getTotalCompactingKVs load)
   :bloom-size-kb              (.getTotalStaticBloomSizeKB load)
   :total-index-size-kb        (.getTotalStaticIndexSizeKB load)
   :write-requests             (.getWriteRequestsCount load)})

(defmacro connection-let
  [[name admin] & body]
  `(let [conn# (HConnectionManager/createConnection (.getConfiguration ~admin))
         ~name conn#]
    (try
      (doall ~@body)
      (finally (.close conn#)))))

;; Get HRegionInfo from HBaseAdmin
(defn- region-locations
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [admin]
  (connection-let
    [conn admin]
    (let [table-descs (.listTables admin)
          table-names (map #(.getName %) table-descs)
          ;; HTables for all tables
          htables (map #(cast HTable (.getTable conn %)) table-names)]
      ;; Map of region info => server location
      (reduce #(merge %1 (.getRegionLocations %2)) {} htables))))

;; Get HRegionInfo from HBaseAdmin
(defn- online-regions
  "Retrieves the information of online regions using HBaseAdmin.getOnlineRegions"
  [admin]
  (let [locations (region-locations admin)
        ;; Reverse key-value pairs and group by servers
        locations (reduce #(update-in %1 [(first %2)] conj (last %2))
                          {}
                          (for [[region-info server-name] locations]
                            [(.getServerName server-name) region-info]))
        ;; Server -> region name -> region info
        locations (for [[server-name region-infos] locations]
                    [server-name
                     (reduce #(apply assoc %1 %2)
                             {}
                             (map (fn [region-info]
                                    [(ByteBuffer/wrap (.getRegionName region-info))
                                     (info->map region-info)])
                                  region-infos))])]
    (into {} locations)))


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
  [admin]
  (let [cluster-status (.getClusterStatus admin)
        server->regions (future (online-regions admin))
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
    (for [server-regions aggregated
          [k v] server-regions]
      (assoc v :name (Bytes/toStringBinary (.array k))))))

(defn region-map
  "Returns a map that associates encoded region name with the name of the
  server that holds the regions"
  [admin]
  (let [loc-region (region-locations admin)
        loc-encoded (for [[info server-name] loc-region]
                      [(.getEncodedName info)
                       (.getServerName server-name)])]
    (into {} loc-encoded)))


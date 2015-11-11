(ns hbase-region-inspector.hbase.impl
  (:require [clojure.set :as set]
            [hbase-region-inspector.hbase.base :as base])
  (:import org.apache.hadoop.hbase.util.Bytes
           java.nio.ByteBuffer))

;; http://archive.cloudera.com/cdh5/cdh/5/hbase-0.98.6-cdh5.3.3/apidocs/index.html

(defn info->map
  "Builds map from HRegionInfo"
  [info]
  (assoc (base/info->map info)
         :table (str (.getTable info))))

(defn load->map
  "Builds map from RegionLoad"
  [load]
  (let [base (base/load->map load)
        loc  (.getDataLocality load)]
    (assoc base
      :store-uncompressed-size-mb (.getStoreUncompressedSizeMB load)
      :locality (* 100 loc)
      :local-size-mb (* loc (:store-file-size-mb base)))))

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
               (.getLoad cluster-status) ; HServerLoad
               (.getRegionsLoad))]       ; byte[] -> RegionLoad
      [(ByteBuffer/wrap region-name) (load->map load)])))

(defn- aggregate-two-sources
  "Merges the maps of region information from the two sources of information.
  Regions that are not found on the both sources are discarded."
  [cluster-status admin server-name]
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
  [admin cluster-status]
  (let [server-names (.getServers cluster-status)
        aggregated (pmap (partial aggregate-two-sources cluster-status admin)
                         server-names)]
    (for [region->info aggregated
          [k v] region->info]
      (assoc v :name (Bytes/toStringBinary (.array k))))))

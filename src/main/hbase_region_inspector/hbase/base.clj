(ns hbase-region-inspector.hbase.base)

(defn info->map
  "Builds map from HRegionInfo"
  [info]
  {:encoded-name (.getEncodedName info)
   ; :table
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
   ; :store-uncompressed-size-mb
   :total-compacting-kvs       (.getTotalCompactingKVs load)
   :bloom-size-kb              (.getTotalStaticBloomSizeKB load)
   :total-index-size-kb        (.getTotalStaticIndexSizeKB load)
   :write-requests             (.getWriteRequestsCount load)})


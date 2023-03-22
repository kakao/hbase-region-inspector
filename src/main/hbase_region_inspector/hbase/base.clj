(ns hbase-region-inspector.hbase.base
  (:import [org.apache.hadoop.hbase
            HRegionInfo]))

(defn info->map
  "Builds map from HRegionInfo"
  [info]
  {:encoded-name (.getEncodedName ^HRegionInfo info)
   ; :table
   :start-key    (.getStartKey ^HRegionInfo info)
   :end-key      (.getEndKey ^HRegionInfo info)
   :meta?        (.isMetaRegion ^HRegionInfo info)})


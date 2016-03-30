(ns hbase-region-inspector.hbase-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.hbase :refer :all])
  (:import org.apache.hadoop.hbase.HBaseTestingUtility))

#_(def hbase (HBaseTestingUtility.))
#_(use-fixtures :once (fn [f]
                        (.startMiniCluster hbase)
                        (f)
                        (.shutdownMiniCluster hbase)))

(deftest test-bytes-comp
  (let [sorted
        (map vec
             (sort bytes-comp (map #(byte-array %) [[0 0]
                                                    [0]
                                                    [2]
                                                    [2 1]
                                                    [2 1 0]
                                                    [3]
                                                    [1 1 1]])))]
    (is (= [[0] [0 0] [1 1 1] [2] [2 1] [2 1 0] [3]] sorted))))

(ns hbase-region-inspector.hbase-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.hbase :refer :all]))

(deftest test-byte-buffer->str
  (let [chars (vec (.getBytes "hello"))
        chars (apply conj chars (map byte (range 10)))
        bytes (byte-array chars)]
    (is (= "hello\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\x07\\x08\\x09"
           (byte-buffer->str bytes)))))

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

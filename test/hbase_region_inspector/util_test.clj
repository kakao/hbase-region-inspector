(ns hbase-region-inspector.util-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.util :refer :all]))

(deftest test-compare-server-names
  (is (> (compare-server-names "foobar10" "foobar2") 0))
  (is (< (compare-server-names "foo-1-bar02" "foo-1-bar010") 0)))

(deftest test-local-ip-addresses
  (is (every?
        #(re-find #"^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$" %)
        (local-ip-addresses))))

(deftest test-keyword->str
  (is (= "Hello" (keyword->str :hello))))

(deftest test-byte-array->str
  (let [chars (vec (.getBytes "hello"))
        chars (apply conj chars (map byte (range 10)))
        bytes (byte-array chars)]
    (is (= "hello\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\x07\\x08\\x09"
           (byte-array->str bytes)))))


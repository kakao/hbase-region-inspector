(ns hbase-region-inspector.util-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.util :refer :all]))

(deftest test-compare-server-names
  (is (> (compare-server-names "foobar10" "foobar2") 0))
  (is (< (compare-server-names "foo-1-bar02" "foo-1-bar010") 0)))

(deftest test-rand-range
  (let [min   10
        max   100
        times 1000]
    (repeatedly times #(is (some (set (range min max))
                                 (rand-range min max))))))

(deftest test-local-ip-addresses
  (is (every?
        #(re-find #"^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$" %)
        (local-ip-addresses))))

(deftest test-keyword->str
  (is (= "Hello" (keyword->str :hello))))

(ns hbase-region-inspector.config-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.config :refer :all :as c]))

(deftest test-kerberos?
  (is (= false (#'c/kerberos? {})))
  (is (= false (#'c/kerberos? {"hbase.security.authentication" "simple"})))
  (is (= false (#'c/kerberos? {"hadoop.security.authentication" "SIMPLE"})))
  (is (= true (#'c/kerberos? {"hbase.security.authentication" "KERBEROS"})))
  (is (= true (#'c/kerberos? {"hbase.security.authentication" "kerberos"})))
  (is (= true (#'c/kerberos? {"hadoop.security.authentication" "KERBEROS"})))
  (is (= true (#'c/kerberos? {"hadoop.security.authentication" "kerberos"}))))

(deftest test-require-key
  (is (= "bar" (#'c/require-key {"foo" "bar"} "foo")))
  (is (thrown? IllegalArgumentException
               (#'c/require-key {"foo" "bar"} "foobar"))))

(deftest test-parse-pair
  (is (= {:foo "bar" :hello "hello world" :baz true :foobar false}
         (#'c/parse-pairs
           "foo=bar hello=\"hello world\" baz=true foobar=false"))))

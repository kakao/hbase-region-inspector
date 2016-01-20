(ns hbase-region-inspector.config-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.java.io :refer [resource file]]
            [hbase-region-inspector.config :refer :all :as c]))

(deftest test-kerberos?
  (is (= false (#'c/kerberos? {})))
  (is (= false (#'c/kerberos? {"hbase.security.authentication"  "simple"})))
  (is (= false (#'c/kerberos? {"hadoop.security.authentication" "SIMPLE"})))
  (is (= true  (#'c/kerberos? {"hbase.security.authentication"  "KERBEROS"})))
  (is (= true  (#'c/kerberos? {"hbase.security.authentication"  "kerberos"})))
  (is (= true  (#'c/kerberos? {"hadoop.security.authentication" "KERBEROS"})))
  (is (= true  (#'c/kerberos? {"hadoop.security.authentication" "kerberos"}))))

(deftest test-require-key
  (is (= "bar" (#'c/require-key {"foo" "bar"} "foo")))
  (is (thrown? IllegalArgumentException
               (#'c/require-key {"foo" "bar"} "foobar"))))

(deftest test-parse-pair
  (is (= {:foo "bar" :hello "hello world" :baz true :foobar false}
         (#'c/parse-pairs
           "foo=bar hello=\"hello world\" baz=true foobar=false"))))

(deftest test-parse-config-file
  (testing "Insecure"
    (let [file (-> "insecure.properties" resource file)
          config (#'c/parse-config-file file)
          {:keys [krb? hbase sys]} config]
      (is (= false krb?))
      (is (empty? sys))
      (are [key val] (= val (hbase key))
           "hbase.zookeeper.quorum" "zookeeper.example.com"
           "hbase.zookeeper.property.clientPort" "2181")))

  (testing "Ticket cache"
    (let [file (-> "ticket-cache.properties" resource file)
          config (#'c/parse-config-file file)
          {:keys [krb? hbase sys context]} config]
      (is krb?)
      (are [key val] (= val (config key))
           :context "KrbClient"
           :useTicketCache true)
      ;; Resolved to absolute path
      (is (str/starts-with? (sys "java.security.auth.login.config") "/"))
      (is (str/ends-with? (sys "java.security.auth.login.config") "ticket-cache-jaas.conf"))
      ;; Path untouched
      (is (= "/etc/krb5.conf" (sys "java.security.krb5.conf")))))

  (testing "Keytab"
    (let [file (-> "keytab.properties" resource file)
          config (#'c/parse-config-file file)
          {:keys [krb? hbase sys context]} config]
      (is krb?)
      (is (= #{"foo"
               "hello"
               "hbase.security.authentication"
               "hadoop.security.authentication"} (set (keys hbase))))
      (is (= #{"java.security.auth.login.config"
               "java.extra.system.property"} (set (keys sys))))
      (are [key val] (= val (hbase key))
           "foo" "bar"
           "hello" "world"
           "hbase.security.authentication" "kerberos"
           "hadoop.security.authentication" "kerberos")
      ;; Resolved to absolute path
      (is (str/starts-with? (sys "java.security.auth.login.config") "/"))
      (is (str/ends-with? (sys "java.security.auth.login.config") "keytab-jaas.conf"))
      ;; Path untouched
      (is (= "keytab-jaas.conf" (sys "java.extra.system.property")))
      (are [key val] (= val (config key))
           :useTicketCache false
           :useKeyTab true
           :keyTab "/secret/hbase.keytab"
           :principal "user@HBASE.EXAMPLE.COM"
           :context "Client"))))

(ns hbase-region-inspector.core-test
  (:require [clojure.test :refer :all]
            [hbase-region-inspector.core :refer :all]))

(deftest test-format-val
  (is (= ["Memstore" "100 MB"] (format-val :memstore-size-mb 100)))
  (is (= ["Memstore" "1 MB"] (format-val :memstore-size-mb 1.2)))
  (is (= ["Data size" "1000 MB"] (format-val :store-file-size-mb 1000)))
  (is (= ["Data size" "1000 MB (2000 MB)"]
         (format-val :store-file-size-mb 1000 {:store-uncompressed-size-mb 2000})))
  (is (= ["Requests" "3000"] (format-val :requests 3000)))
  (is (= ["Requests" "3000 (3.14/sec)"]
         (format-val :requests 3000 {:requests-rate 3.141592})))
  (is (= ["Requests" "3000 (314/sec)"]
         (format-val :requests 3000 {:requests-rate 314.1592}))))

(deftest test-build-region-popover
  (is (= (str
           "<h3>foobar <small>baz</small></h3>"
           "<table class=\"table table-condensed table-striped\">"
           "<tbody><tr><th class=\"col-xs-2\">Data size</th><td>1000 MB (2000 MB)</td></tr></tbody></table>")
         (build-region-popover {:table "foobar"
                                :encoded-name "baz"
                                :store-file-size-mb 1000
                                :store-uncompressed-size-mb 2000}))))

(deftest test-byte-buffers->str
  (is (= {:start-key "hello"
          :end-key "\\x00\\x01\\x02\\x03\\x04"
          :untouched 1234}
         (byte-buffers->str
           {:start-key (.getBytes "hello")
            :end-key (byte-array (map byte (range 5)))
            :untouched 1234}))))

(def ^:private servers
  {"alpha" {}
   "beta"  {}
   "gamma" {}})

(def ^:private regions
  ;; foo-sum 12
  ;; bar-sum 14
  ;; baz-sum 16
  [{:server "alpha" :table "foo" :start-key (.getBytes "d") :val1 1 :val2 1}
   {:server "alpha" :table "bar" :start-key (.getBytes "c") :val1 1 :val2 2}
   {:server "alpha" :table "baz" :start-key (.getBytes "b") :val1 2 :val2 3}
   {:server "alpha" :table "foo" :start-key (.getBytes "a") :val1 2 :val2 4}
   {:server "beta"  :table "bar" :start-key (.getBytes "h") :val1 3 :val2 5}
   {:server "beta"  :table "baz" :start-key (.getBytes "g") :val1 3 :val2 6}
   {:server "beta"  :table "foo" :start-key (.getBytes "f") :val1 4 :val2 7}
   {:server "beta"  :table "bar" :start-key (.getBytes "e") :val1 4 :val2 8}
   {:server "gamma" :table "baz" :start-key (.getBytes "l") :val1 5 :val2 9}
   {:server "gamma" :table "foo" :start-key (.getBytes "k") :val1 5 :val2 10}
   {:server "gamma" :table "bar" :start-key (.getBytes "j") :val1 6 :val2 11}
   {:server "gamma" :table "baz" :start-key (.getBytes "i") :val1 6 :val2 12}])

(deftest test-regions-by-servers
  (let [abg (fn [args]
              (let [{servers :servers :as result}
                    (regions-by-servers (merge {:regions regions :servers servers} args))
                    {:strs [alpha beta gamma]}
                    (into {} (for [server servers] [(:name server) server]))]
                (merge result {:alpha alpha :beta beta :gamma gamma
                               :checker (juxt :max :sum)})))]
    (testing "all tables"
      (let [{:keys [tables alpha beta gamma checker]} (abg {:metric :val1 :sort :metric})]
        (is (= #{"foo" "bar" "baz"} (set tables)))
        (is (= [22 6] (checker alpha)))
        (is (= [22 14] (checker beta)))
        (is (= [22 22] (checker gamma)))
        (is (= [3 4 2 1] (map :val2 (:regions alpha))))
        (is (= [8 7 6 5] (map :val2 (:regions beta))))
        (is (= [12 11 9 10] (map :val2 (:regions gamma))))))
    (testing "subset of tables"
      (let [{:keys [tables alpha beta gamma checker]} (abg {:metric :val1 :sort :metric :tables ["foo" "baz"]})]
        (is (= #{"foo" "bar" "baz"} (set tables)))
        (is (= [16 5] (checker alpha)))
        (is (= [16 7] (checker beta)))
        (is (= [16 16] (checker gamma)))
        (is (= [3 4 1] (map :val2 (:regions alpha))))
        (is (= [7 6] (map :val2 (:regions beta))))
        (is (= [12 9 10] (map :val2 (:regions gamma))))))
    (testing "sort by table sum"
      (let [{:keys [tables alpha beta gamma checker]} (abg {:metric :val1 :sort :table :tables []})]
        (is (= #{"foo" "bar" "baz"} (set tables)))
        (is (= [3 2 4 1] (map :val2 (:regions alpha))))
        (is (= [6 8 5 7] (map :val2 (:regions beta))))
        (is (= [12 9 11 10] (map :val2 (:regions gamma))))))))

(deftest test-regions-by-tables
  (testing "sort by metric"
    (let [{result :tables} (regions-by-tables {:regions regions :metric :val1 :sort :metric})
          [bar baz foo] result
          [bar-regions baz-regions foo-regions] (map :regions result)]
      ;; Tables are sorted by their names
      (is (= ["bar" "baz" "foo"] (map :name result)))
      (is (= 12 (:sum foo)))
      (is (= 14 (:sum bar)))
      (is (= 16 (:sum baz)))
      (is (= [10 7 4 1] (map :val2 foo-regions)))
      (is (= [11 8 5 2] (map :val2 bar-regions)))
      (is (= [12 9 6 3] (map :val2 baz-regions)))))
  (testing "sort by metric - subset of tables"
    (let [{:keys [tables all-tables]} (regions-by-tables {:regions regions :metric :val1 :sort :metric :tables ["foo" "baz"]})
          [baz foo] tables
          [baz-regions foo-regions] (map :regions tables)]
      ;; Tables are sorted by their names
      (is (= ["bar" "baz" "foo"] all-tables))
      (is (= ["baz" "foo"] (map :name tables)))
      (is (= 12 (:sum foo)))
      (is (= 16 (:sum baz)))
      (is (= [10 7 4 1] (map :val2 foo-regions)))
      (is (= [12 9 6 3] (map :val2 baz-regions)))))
  (testing "sort by start-key"
    (let [{result :tables} (regions-by-tables {:regions regions :metric :val1 :sort :start-key})
          [bar baz foo] result
          [bar-regions baz-regions foo-regions] (map :regions result)]
      ;; Tables are, again, sorted by their names
      (is (= ["bar" "baz" "foo"] (map :name result)))
      (is (= 12 (:sum foo)))
      (is (= 14 (:sum bar)))
      (is (= 16 (:sum baz)))
      (is (= [4 1 7 10] (map :val2 foo-regions)))
      (is (= [2 8 5 11] (map :val2 bar-regions)))
      (is (= [3 6 12 9] (map :val2 baz-regions))))))

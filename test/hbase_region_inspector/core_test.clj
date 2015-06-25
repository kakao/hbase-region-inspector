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

(deftest test-build-html
  (is (= (str
           "<h3>foobar <small>baz</small></h3>"
           "<table class=\"table table-condensed table-striped\">"
           "<tbody><tr><th class=\"col-xs-2\">Data size</th><td>1000 MB (2000 MB)</td></tr></tbody></table>")
         (build-html {:table "foobar"
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

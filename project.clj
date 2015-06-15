(defproject hbase-region-inspector "0.1.0-SNAPSHOT"
  :description "HBase region dashboard"
  :url "http://example.com/FIXME"
  :license {:name "MIT"}
  :repositories [["cloudera-releases"
                  "https://repository.cloudera.com/artifactory/cloudera-repos"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.4.0-RC1"]
                 [ring/ring-devel "1.4.0-RC1"]
                 [ring/ring-jetty-adapter "1.4.0-RC1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.3.4"]
                 [hiccup "1.0.5"]
                 [selmer "0.8.2"]]
  :source-paths ["src/main"]
  :plugins [[lein-ring "0.9.4"]    ; lein ring server
            [lein-bin "0.3.5"]     ; lein bin
            [codox "0.8.12"]       ; lein doc
            [lein-pprint "1.1.2"]] ; lein pprint
  :ring {:handler hbase-region-inspector.core/app
         :nrepl {:start? true
                 :port 9999}}

  :main ^:skip-aot hbase-region-inspector.core
  :target-path "target/%s"
  :profiles
  {:0.94 {:bin {:name "hbase-region-inspector-0.94"}
          :source-paths ["src/hbase-0.94"]
          ;; lein with-profile 0.94 deps :tree
          :dependencies [[org.apache.hbase/hbase "0.94.15-cdh4.7.1"
                          :exclusions
                          [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                         [org.apache.hadoop/hadoop-common "2.0.0-cdh4.7.1"
                          :exclusions
                          [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                         [org.slf4j/slf4j-api "1.7.12"]
                         [org.slf4j/slf4j-log4j12 "1.7.12"]]}
   :0.98 {:bin {:name "hbase-region-inspector-0.98"}
          :source-paths ["src/hbase-0.98"]
          :dependencies [[org.apache.hbase/hbase-client "0.98.6-cdh5.3.3"]
                         [org.apache.hbase/hbase-common "0.98.6-cdh5.3.3"]]}
   :uberjar {:aot :all}})

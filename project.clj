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
                 [selmer "0.8.2"]
                 [org.apache.hbase/hbase-client "0.98.6-cdh5.3.3"]
                 [org.apache.hbase/hbase-common "0.98.6-cdh5.3.3"]]
  :plugins [[lein-ring "0.9.4"] ; lein ring server
            [lein-bin "0.3.5"]  ; lein bin
            [codox "0.8.12"]]   ; lein doc
  :bin {:name "hbase-region-inspector"}
  :ring {:handler hbase-region-inspector.core/app
         :nrepl {:start? true
                 :port 9999}}

  :main ^:skip-aot hbase-region-inspector.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

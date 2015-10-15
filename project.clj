(def project-version "0.2.7")
(def profiles [:0.94 :0.98])
(def bins (into {} (for [profile profiles]
                     [profile (format "hbase-region-inspector-%s-%s"
                                      (name profile) project-version)])))
(def jars (into {} (for [[k v] bins] [k (str v ".jar")])))

(defproject hbase-region-inspector "0.2.7" ; Can't use ~ yet
  :description "HBase region dashboard"
  :url "http://example.com/FIXME"
  :license {:name "MIT"}
  :repositories [["cloudera-releases"
                  "https://repository.cloudera.com/artifactory/cloudera-repos"]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.3.4"]
                 [hiccup "1.0.5"]
                 [selmer "0.8.2"]]
  :source-paths ["src/main"]
  :target-path "target/%s"
  :plugins [[lein-ring "0.9.4"]    ; lein ring server
            [lein-bin "0.3.5"]     ; lein bin
            [codox "0.8.12"]       ; lein doc
            [com.jakemccrary/lein-test-refresh "0.10.0"]
            [lein-pprint "1.1.2"]] ; lein pprint
  :ring {:handler hbase-region-inspector.core/app
         :nrepl {:start? true :port 9999}}
  :bin {:jvm-opts ~(if-let [jvm-opts (or (System/getenv "JVM_OPTS")
                                         (System/getenv "JAVA_OPTS"))]
                     (clojure.string/split jvm-opts #"\s+")
                     ["-Xmx2g"])}
  :jvm-opts ["-Xmx2g"]
  :main ^:skip-aot hbase-region-inspector.core

  :uberjar-exclusions [#".*/\.module-cache/"]

  ;; https://github.com/technomancy/leiningen/issues/1718
  :profiles
  {:test {:resource-paths ["test/resources"]}
   :0.94-test {:dependencies [[org.apache.hbase/hbase "0.94.15-cdh4.7.1" :classifier "tests"]
                              ;; DistributedFileSystem
                              [org.apache.hadoop/hadoop-hdfs "2.0.0-cdh4.7.1"]
                              ;; MiniDFSCluster
                              [org.apache.hadoop/hadoop-minicluster "2.0.0-cdh4.7.1"]]}
   :0.94 ^:leaky {:bin {:name ~(:0.94 bins)}
                  :uberjar-name ~(:0.94 jars)
                  :target-path  "target/0.94"
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
   :0.98-test {:dependencies [[org.apache.hbase/hbase-testing-util "0.98.6-cdh5.3.3"]]}
   :0.98 ^:leaky {:bin {:name ~(:0.98 bins)}
                  :uberjar-name ~(:0.98 jars)
                  :target-path  "target/0.98"
                  :source-paths ["src/hbase-0.98"]
                  :dependencies [[org.apache.hbase/hbase-client "0.98.6-cdh5.3.3"]
                                 [org.apache.hbase/hbase-common "0.98.6-cdh5.3.3"]]}
   :uberjar {:aot :all}})

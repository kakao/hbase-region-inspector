(def project-version "0.3.2")
(def current :1.0)
(defn bin [profile]
  (str "hbase-region-inspector-" project-version (when (not= profile current)
                                                   (str "-" (name profile)))))
(defn jar [profile & [suffix]]
  (str (bin profile) suffix ".jar"))

(defproject hbase-region-inspector project-version
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
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [compojure "1.3.4"]
                 [hiccup "1.0.5"]
                 [selmer "0.8.2"]]
  :source-paths ["src/main"]
  :target-path "target/%s"
  :plugins [[lein-ring "0.9.4"]        ; lein ring server
            [lein-bin "0.3.5"]         ; lein bin
            [codox "0.8.12"]           ; lein doc
            [com.jakemccrary/lein-test-refresh "0.10.0"]
            [lein-pprint "1.1.2"]      ; lein pprint
            [jonase/eastwood "0.2.1"]] ; lein eastwood
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
   :cdh4-test {:dependencies [[org.apache.hbase/hbase "0.94.15-cdh4.7.1" :classifier "tests"]
                              ;; DistributedFileSystem
                              [org.apache.hadoop/hadoop-hdfs "2.0.0-cdh4.7.1"]
                              ;; MiniDFSCluster
                              [org.apache.hadoop/hadoop-minicluster "2.0.0-cdh4.7.1"]]}
   :cdh4 ^:leaky {:bin {:name ~(bin :cdh4)}
                  :jar-name ~(jar :cdh4)
                  :uberjar-name ~(jar :cdh4 "-standalone")
                  :target-path  "target/cdh4"
                  :source-paths ["src/hbase-cdh4"]
                  ;; lein with-profile cdh4 deps :tree
                  :dependencies [[org.apache.hbase/hbase "0.94.15-cdh4.7.1"
                                  :exclusions
                                  [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                                 [org.apache.hadoop/hadoop-common "2.0.0-cdh4.7.1"
                                  :exclusions
                                  [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                                 [org.slf4j/slf4j-api "1.7.12"]
                                 [org.slf4j/slf4j-log4j12 "1.7.12"]]}
   :1.0-test {:dependencies [[org.apache.hbase/hbase-testing-util "1.0.0"]]}
   :1.0 ^:leaky {:bin {:name ~(bin :1.0)}
                 :jar-name ~(jar :1.0)
                 :uberjar-name ~(jar :1.0 "-standalone")
                 :target-path  "target/1.0"
                 :source-paths ["src/hbase-1.0"]
                 :dependencies [[org.apache.hbase/hbase-client "1.0.0"]
                                [org.apache.hbase/hbase-common "1.0.0"]]}
   :uberjar {:aot :all}})

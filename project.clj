(def project-version "0.3.8")
(def current :hbase1)
(defn bin [profile]
  (str "hbase-region-inspector-" project-version (when (not= profile current)
                                                   (str "-" (name profile)))))
(defn jar [profile & [suffix]]
  (str (bin profile) suffix ".jar"))

(defproject hbase-region-inspector project-version
  :description "HBase region dashboard"
  :url "https://github.com/kakao/hbase-region-inspector"
  :license {:name "Apache License 2.0"}
  :repositories [["cloudera-releases"
                  "https://repository.cloudera.com/artifactory/cloudera-repos"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
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
            [jonase/eastwood "1.3.0"]] ; lein eastwood
  :ring {:handler hbase-region-inspector.core/app
         :nrepl {:start? true :port 9999}}
  :jvm-opts ["-Xmx2g" "-Dclojure.compiler.direct-linking=true"]
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
                  :source-paths ["src/cdh4"]
                  ;; lein with-profile cdh4 deps :tree
                  :dependencies [[org.apache.hbase/hbase "0.94.15-cdh4.7.1"
                                  :exclusions
                                  [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                                 [org.apache.hadoop/hadoop-common "2.0.0-cdh4.7.1"
                                  :exclusions
                                  [javax.xml.bind/jaxb-api org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                                 [org.slf4j/slf4j-api "1.7.12"]
                                 [org.slf4j/slf4j-log4j12 "1.7.12"]]}
   :hbase1-test {:dependencies [[org.apache.hbase/hbase-testing-util "1.0.0"]]}
   :hbase1 ^:leaky {:bin {:name ~(bin :hbase1)}
                 :jar-name ~(jar :hbase1)
                 :uberjar-name ~(jar :hbase1 "-standalone")
                 :target-path  "target/hbase1"
                 :source-paths ["src/hbase1"]
                 :dependencies [[org.apache.hbase/hbase-client "1.0.0"]
                                [org.apache.hbase/hbase-common "1.0.0"]
                                [org.apache.zookeeper/zookeeper "3.5.7"]]}
   :hbase2-test {:dependencies [[org.apache.hbase/hbase-testing-util "2.5.3"
                              :exclusions [net.minidev/json-smart org.glassfish.web/javax.servlet.jsp]]]}
   :hbase2 ^:leaky {:bin {:name ~(bin :hbase2)}
                    :jar-name ~(jar :hbase2)
                    :uberjar-name ~(jar :hbase2 "-standalone")
                    :target-path  "target/hbase2"
                    ;; No difference between hbase1 and hbase2
                    :source-paths ["src/hbase1"]
                    :dependencies [[org.apache.hbase/hbase-client "2.5.3"
                                    :exclusions [net.minidev/json-smart]]
                                   [org.apache.hbase/hbase-common "2.5.3"
                                    :exclusions [net.minidev/json-smart]]
                                   [org.apache.zookeeper/zookeeper "3.5.7"]]}
   :uberjar {:aot :all}})

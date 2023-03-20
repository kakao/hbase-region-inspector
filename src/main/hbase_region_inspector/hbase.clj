(ns hbase-region-inspector.hbase
  (:require [clojure.string :as str]
            [hbase-region-inspector.util :as util]
            [hbase-region-inspector.hbase.impl :as hbase-impl])
  (:import [org.apache.hadoop.hbase
            client.HBaseAdmin client.Admin util.Bytes
            HBaseConfiguration ClusterStatus
            ServerLoad]
           org.apache.hadoop.security.UserGroupInformation
           org.apache.hadoop.conf.Configuration))

(defn server-load->map
  "Transforms ServerLoad object into clojure map"
  [load]
  (assoc
    {:max-heap-mb        (.getMaxHeapMB ^ServerLoad load)
     :used-heap-mb       (.getUsedHeapMB ^ServerLoad load)
     :regions            (.getNumberOfRegions ^ServerLoad load)
     :requests-rate      (.getNumberOfRequests ^ServerLoad load)
     :store-files        (.getStorefiles ^ServerLoad load)
     :store-file-size-mb (.getStorefileSizeInMB ^ServerLoad load)}
    :store-uncompressed-size-mb
    (some->> load
             str
             (re-find #"storefileUncompressedSizeMB=([0-9]+)")
             last
             Integer/parseInt)))

(defn- collect-server-info
  "Collects server statistics"
  [^ClusterStatus cluster-status]
  (let [server-names (.getServers cluster-status)
        server-loads (map #(.getLoad cluster-status %) server-names)]
    (into
      {}
      (map (fn [[name load]]
             (let [name (str name)]
               [name
                (assoc (server-load->map load) :name name)]))
           (zipmap server-names server-loads)))))

(defn collect-region-info
  "Collects the region information. You can pass ClusterStatus object to avoid
  repetitive creation of it."
  ([admin]
   (hbase-impl/collect-region-info admin (.getClusterStatus ^Admin admin)))
  ([admin cluster-status]
   (hbase-impl/collect-region-info admin cluster-status)))

(defn collect-info
  "Collects server and region statistics"
  [admin]
  (let [cluster-status (.getClusterStatus ^Admin admin)]
    {:servers      (doall (collect-server-info cluster-status))
     :regions      (doall (collect-region-info admin cluster-status))
     :has-locality (-> (.getHBaseVersion cluster-status) first (not= \0))}))

(def bytes-comp
  "Comparator for byte arrays"
  Bytes/BYTES_COMPARATOR)

(defn- set-sys!
  "Sets system properties and prints debug log"
  [k v]
  (util/debug (format "System/setProperty: %s => %s" k v))
  (System/setProperty k v))

(defn set-krb-properties!
  "Sets system properties for Kerberos authentication"
  [config]
  (let [realm (-> (:hbase config)
                  (get "hbase.master.kerberos.principal")
                  (str/replace #".*@" ""))
        krb-config (sun.security.krb5.Config/getInstance)
        kdc-list   (.getKDCList krb-config realm)]
    (set-sys! "java.security.krb5.realm" realm)
    (set-sys! "java.security.krb5.kdc" kdc-list)))

(def ^Configuration build-hbase-conf
  "Builds HBaseConfiguration from the given map of configuration. The built
  objects are cached."
  (memoize
    (fn [{:keys [sys hbase
                 krb? useKeyTab principal keyTab] :as conf}]
      ;; Set system properties (java.*)
      (doseq [[k v] sys] (set-sys! k v))

      ;; Build HBaseConfiguration
      ;; - https://support.pivotal.io/hc/en-us/articles/200933006-Hbase-application-hangs-indefinitely-connecting-to-zookeeper
      (let [hbc (doto (HBaseConfiguration/create)
                  (.setInt "hbase.client.retries.number" 1)
                  (.setInt "zookeeper.recovery.retry" 1))]
        (doseq [[k v] hbase] (.set hbc k v))
        (when krb?
          (set-krb-properties! conf)
          (sun.security.krb5.Config/refresh)
          (UserGroupInformation/setConfiguration hbc)
          (if useKeyTab
            (UserGroupInformation/loginUserFromKeytab principal keyTab)
            (let [method
                  (.getMethod UserGroupInformation
                              "loginUserFromSubject"
                              (into-array Class [javax.security.auth.Subject]))]
              (.invoke method nil (object-array [nil]))))
          (util/debug "Current user: " (UserGroupInformation/getCurrentUser)))
        hbc))))

(defn rs-info-port
  "Returns the port for the RegionServer web UI"
  [conf]
  (.getInt (build-hbase-conf conf) "hbase.regionserver.info.port" 60030))

(defn connect-admin
  "Creates HBaseAdmin instance with the given configuration.
  TODO: HBaseAdmin is deprecated in favor of Admin in the recent versions of
  HBase"
  [conf]
  (HBaseAdmin. (build-hbase-conf conf)))

(defmacro with-admin
  "Evaluates body with HBaseAdmin created with conf bound to name and
  finally closes it."
  [[name conf] & body]
  `(with-open [~name ^Admin (~connect-admin ~conf)] ~@body))

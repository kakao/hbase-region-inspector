(ns hbase-region-inspector.hbase
  (:require [clojure.string :as str]
            [hbase-region-inspector.util :as util]
            [hbase-region-inspector.hbase.impl :as hbase-impl])
  (:import org.apache.hadoop.hbase.client.HBaseAdmin
           org.apache.hadoop.hbase.HBaseConfiguration
           org.apache.hadoop.hbase.util.Bytes
           org.apache.hadoop.security.UserGroupInformation))

(defn server-load->map
  "Transforms ServerLoad object into clojure map"
  [load]
  (assoc
    {:max-heap-mb        (.getMaxHeapMB load)
     :used-heap-mb       (.getUsedHeapMB load)
     :regions            (.getNumberOfRegions load)
     :requests-rate      (.getNumberOfRequests load)
     :store-files        (.getStorefiles load)
     :store-file-size-mb (.getStorefileSizeInMB load)}
    :store-uncompressed-size-mb
    (some->> load
             str
             (re-find #"storefileUncompressedSizeMB=([0-9]+)")
             last
             Integer/parseInt)))

(defn- collect-server-info
  "Collects server statistics"
  [cluster-status]
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
  ([admin]
   (hbase-impl/collect-region-info admin (.getClusterStatus admin)))
  ([admin cluster-status]
   (hbase-impl/collect-region-info admin cluster-status)))

(defn collect-info
  "Collects server and region statistics"
  [admin]
  (let [cluster-status (.getClusterStatus admin)]
    {:servers (doall (collect-server-info cluster-status))
     :regions (doall (collect-region-info admin cluster-status))}))

(def bytes-comp Bytes/BYTES_COMPARATOR)

(defn byte-buffer->str
  "Returns the string representation of a bytes array"
  [buf]
  (Bytes/toStringBinary buf))

(defn- set-sys! [k v]
  (util/debug (format "System/setProperty: %s => %s" k v))
  (System/setProperty k v))

(defn set-krb-properties! [config]
  (let [realm (-> (:hbase config)
                  (get "hbase.master.kerberos.principal")
                  (str/replace #".*@" ""))
        krb-config (sun.security.krb5.Config/getInstance)
        kdc-list   (.getKDCList krb-config realm)]
    (set-sys! "java.security.krb5.realm" realm)
    (set-sys! "java.security.krb5.kdc" kdc-list)))

(def build-hbase-conf
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

(defn connect-admin [conf]
  (HBaseAdmin. (build-hbase-conf conf)))

(defmacro admin-let
  [[name conf] & body]
  `(let [admin# (~connect-admin ~conf)
         ~name admin#]
     (try (doall ~@body) (finally (.close admin#)))))

(ns hbase-region-inspector.config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hbase-region-inspector.util :as util])
  (:import java.util.Properties
           java.lang.System))

(defn- kerberos?
  "Determines if Kerberos authentication should be enabled"
  [props]
  (some?
    (some #(= "KERBEROS" (str/upper-case (get props % "")))
          ["hbase.security.authentication"
           "hadoop.security.authentication"])))

(defn- require-key
  "Checks if a-map contains key and returns the corresponding value.
  If key is not found, IllegalArgumentException is thrown."
  [a-map key]
  (or (a-map key)
      (throw (IllegalArgumentException. (str key " not found")))))

(defn- validate
  "Validates configuration for Kerberos authentication."
  [config]
  (let [{:keys [krb? useKeyTab keyTab principal context hbase sys]} config]
    (require-key hbase "hbase.zookeeper.quorum")
    (when krb?
      (require-key hbase "hbase.security.authentication")
      (require-key hbase "hadoop.security.authentication")
      (require-key hbase "hbase.master.kerberos.principal")
      (require-key hbase "hbase.regionserver.kerberos.principal")
      (require-key sys   "java.security.krb5.conf")
      (require-key sys   "java.security.auth.login.config")
      (when useKeyTab
        (if-not keyTab
          (throw (IllegalArgumentException.
                   "JAAS configuration is missing keyTab entry")))
        (if-not (.exists (io/file keyTab))
          (throw (IllegalArgumentException.
                   (str "Keytab file is missing: " keyTab ". "
                        "Please consider using absolute path."))))
        (if-not principal
          (throw (IllegalArgumentException.
                   "JAAS configuration is missing keyTab entry"))))))
  config)

(defn- parse-pairs
  "Parses key-value pairs in JAAS configuration and returns a map"
  [string]
  (into
    {}
    (for [[_ k v1 v2] (re-seq #"(\S+)=(?:\"([^\"]+)\"|(\S+))" string)]
      [(keyword k)
       (let [v (or v1 v2)]
         (condp re-find v
           #"^true;?$"  true
           #"^false;?$" false
           v))])))

(defn- parse-jaas
  "Parses JAAS configuration file"
  [path]
  (let [content (slurp path)
        contexts
        (map #(assoc (parse-pairs (last %)) :context (nth % 1))
             (re-seq
               #"(?si)(\S+)\s*\{.*?Krb5LoginModule\s*required(.*?)\}"
               content))
        chosen (first contexts)]
    (cond (> (count contexts) 1)
          (util/warn
            (format "Multiple configurations found in %s. Using \"%s\"."
                    path (:context chosen)))
          (nil? chosen)
          (util/warn "No configuration found in " path))
    chosen))

(defn- parse-config-file
  "Parses hbase-region-inspector configuration file and returns the map
  representation of the configuration"
  [^java.io.File file]
  (let [locate (partial util/locate-file (.getParent file))
        rdr    (io/reader file)
        props  (into {} (doto (Properties.) (.load rdr)))
        [sys hb] (map #(into {} %)
                      ((juxt filter remove)
                       #(.startsWith (key %) "java.") props))
        sys (into {}
                  (for [[k v] sys]
                    [k (if (#{"java.security.auth.login.config"
                              "java.security.krb5.conf"} k) (locate v) v)]))
        krb? (kerberos? hb)
        jaas (sys "java.security.auth.login.config")]
    (merge {:krb? krb? :hbase hb :sys sys}
           (if krb? (parse-jaas jaas)))))

(defn- build-config
  "Builds basic configuration map from ZooKeeper quorum"
  [quorum]
  (let [[quorum port] (str/split quorum #"/")]
    {:krb?  false
     :hbase (merge {"hbase.zookeeper.quorum" quorum}
                   (if port
                     {"hbase.zookeeper.property.clientPort" port}))}))

(defn parse
  "Returns the configuration map for the given argument which can be either
  a ZooKeeper quorum string or a configuration file path"
  [spec]
  (let [file (io/file spec)
        conf (if (.exists file)
               (parse-config-file file)
               (build-config spec))]
    (validate
      (assoc conf :zookeeper
             (get-in conf [:hbase "hbase.zookeeper.quorum"])))))


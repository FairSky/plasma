(ns plasma.config
  (:use plasma.util))

(defonce config*
  (ref {:plasma-version      "0.1.0"            ; version number, for protocol versioning
        :protocol            "plasma"           ; default peer protocol
        :peer-port            4242              ; listening for incoming socket connections
        :presence-port        4243              ; UDP broadcast port for presence messaging
        :presence-period      5000              ; presence broadcast period in ms
        :connection-cache-limit 50                ; max open connections
        :peer-id              (uuid)            ; TODO: store this somewhere?
        :meta-id              "UUID:META"       ; special UUID for graph meta-data node

;        :db-path              "db"
        }))

(defn config
  "Lookup a config value."
  ([] @config*)
  ([key] (get @config* key))
  ([k v] (dosync (alter config* k v))))


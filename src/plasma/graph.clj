(ns plasma.graph
  (:use
    [plasma util config url]
    [plasma.jiraph mem-layer]
    [lamina core]
    [aleph formats]
    [jiraph graph]
    [clojure.contrib.core :only (dissoc-in)]))

; Special uuid used to query for a graph's root node.
(def ROOT-ID "UUID:ROOT")

(def *current-query* nil)
(def *current-binding* nil)
(def *current-path* nil)
(def *current-edge-preds* nil)
(def *proxy-results* nil)

(defmulti peer-sender
  (fn [url]
    (keyword (:proto (url-map url)))))

(declare find-node)

(defn get-edges [node & [pred]]
  (let [n (find-node node)]
    (if pred
      (edges n pred)
      (edges n))))

(defn incoming-nodes
  "Return the set of source nodes for all incoming edges to the node with uuid."
  [uuid]
  (get-incoming :graph uuid))

(defn root-node-id
  "Get the UUID of the root node of the graph."
  []
  (:root (meta *graph*)))

(defn make-node
  "Create a node in the current graph.  Returns the ID of the new node.

  (make-node)         ; create a node with a generated UUID and no properties
  (make-node \"foo\") ; create a node with ID foo and no properties
  (make-node {:a 123 :b \"asdf\"}) ; generates a UUID and saves props in node
  (make-node \"foo\" {:a 1324}) ; save node with id foo and props.
  "
  ([]
   (make-node (uuid) {}))
  ([arg]
   (if (map? arg)
     (if (:id arg)
       (make-node (:id arg) arg)
       (make-node (uuid) arg))
     (make-node arg {})))
  ([id props]
   (add-node! :graph id :id id props)
   id))

(defn remove-node
  "Remove a node and all of its incoming edges from the graph."
  [uuid]
  (let [incoming-sources (incoming-nodes uuid)]
    (doseq [src incoming-sources] ; remove incoming edges
      (update-node! :graph src #(dissoc-in % [:edges uuid])))
    (delete-node! :graph uuid)))

(defn make-proxy-node
  "Create a proxy node, representing a node on a remote graph which can be located by accessing the given url."
  [uuid url]
  (make-node uuid {:proxy url}))

(defn proxy-node?
  "Check whether the node with the given UUID is a proxy node."
  [uuid]
  (contains? (find-node uuid) :proxy))

(defn make-edge
  "Create an edge from src to tgt with the associated properties.  At minimum
  there must be a :label property.

    (make-edge alice bob :label :friend)
  "
  [src tgt label-or-props]
  (let [src (if (= ROOT-ID src)
              (root-node-id)
              src)
        props (cond
                (keyword? label-or-props) {:label label-or-props}
                (and (map? label-or-props)
                     (contains? label-or-props :label)) label-or-props
                :default
                (throw (Exception. "make-edge requires either a keyword label or a map containing the :label property.")))]
    (update-node! :graph src #(assoc-in % [:edges tgt] props))))

(defn remove-edge
  "Remove the edge going from src to tgt.  Optionally takes a predicate
  function that will be passed the property map for the edge, and the
  edge will only be removed if the predicate returns true."
  [src tgt & [pred]]
  (update-node! :graph src
    (fn [node]
      (if (fn? pred)
        (if (pred (get (:edges node) tgt))
          (dissoc-in node [:edges tgt])
          node)
        (dissoc-in node [:edges tgt])))))

(defn node-assoc
  "Associate key/value pairs with a given node id."
  [uuid & key-vals]
  (apply assoc-node! :graph uuid (apply hash-map key-vals)))

(defn find-node
  "Lookup a node map by UUID."
  [uuid]
  (when-not *graph*
    (throw (Exception. "Cannot find-node without a bound graph.
For example:\n\t(with-graph G (find-node id))\n")))
  (let [uuid (if (= uuid ROOT-ID)
               (root-node-id)
               uuid)]
    (if-let [node (get-node :graph uuid)]
      (assoc node
             :id uuid
             :type :node))))

(defn- init-new-graph
  [root-id]
  (let [root (make-node root-id {:label :root})
        meta (make-node (config :meta-id) {:root root})]
    {:root root}))

(defn open-graph
  "Open a graph database located at path, creating it if it doesn't already exist."
  [& [path]]
  (let [g (if path
            {:graph (layer path)}
            {:graph (ref-layer)})]
    (with-graph g
      (let [meta (find-node (config :meta-id))
            meta (if meta
                   meta
                   (init-new-graph (uuid)))]
        (with-meta g meta)))))

(defn clear-graph
  []
  (truncate!)
  (init-new-graph (:root (meta *graph*))))

(defmacro with-nodes! [bindings & body]
  (let [nodes (map (fn [[node-sym props]]
                     (let [props (if (keyword? props)
                                   {:label props}
                                   props)]
                       [node-sym `(make-node ~props)]))
                   (partition 2 bindings))
        nodes (vec (apply concat nodes))]
    `(let ~nodes ~@body)))

(comment defn remove-edge [from to]
  (delete-edge! :graph from to))


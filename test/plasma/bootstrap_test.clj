(ns plasma.bootstrap-test
  (:use [plasma core util connection peer bootstrap]
        jiraph.graph
        test-utils
        clojure.test
        clojure.stacktrace)
  (:require [logjam.core :as log]
            [lamina.core :as lamina]
            [plasma.query :as q]))

(log/file [:peer :bootstrap :con] "peer.log")

(defn make-peers
  "Create n peers, each with a monotically increasing port number.
  Then run (fun i) with the peer graph bound to initialize each peer,
  and i being the index of the peer being created."
  [n start-port fun]
  (doall
    (for [i (range n)]
        (let [p (peer (str "db/peer-" i)
                      {:port (+ start-port i)})]
          (with-graph (:graph p)
                      (fun i)
                      p)))))

(deftest bootstrap-test
  (let [port (+ 5000 (rand-int 5000))
        strapper (bootstrap-peer "db/strapper" {:port port})
        strap-url (plasma-url "localhost" port)
        n-peers 10
        peers (make-peers n-peers (inc port)
                (fn [i]
                  (clear-graph)
                  (let [root-id (root-node)]
                    (node-assoc root-id :peer-id i)
                    (edge root-id (node) :label :net))))]
    (is (= 1 (count (query strapper (q/path [:net]) 200))))
    (try
      (doall
        (for [p peers]
          (do
            (Thread/sleep 200)
            (bootstrap p strap-url))))
      (Thread/sleep 300)
      (is (= n-peers (count (query strapper (q/path [:net :peer])
                                   200))))
      (is (= N-BOOTSTRAP-PEERS (count (query (last peers) (q/path [:net :peer])
                                             200))))
      (finally
        (close strapper)
        (close-peers peers)))))

(comment
(def strap (bootstrap-peer "db/strapper" {:port 2345}))
(def strap-url (plasma-url "localhost" 2345))

(def peers (make-peers 2 2223
                (fn [i]
                  (clear-graph)
                  (let [root-id (root-node)]
                    (node-assoc root-id :peer-id i)
                    (edge root-id (node) :label :net)))))

;(def con (get-connection (:manager (first peers)) strap-url))
(bootstrap (first peers) (plasma-url "localhost" 2234))
(peer-query con (-> (q/path [peer [:net :peer]])
                                  (q/choose N-BOOTSTRAP-PEERS)
                                  (q/project 'peer [:proxy :id]))
                              500)
  )
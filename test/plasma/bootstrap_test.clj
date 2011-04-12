(ns plasma.bootstrap-test
  (:use [plasma core util connection peer bootstrap]
        test-utils
        clojure.test
        clojure.stacktrace)
  (:require [logjam.core :as log]
            [lamina.core :as lamina]
            [plasma.query :as q]))

;(log/file [:peer :bootstrap :con] "peer.log")

(defn make-peers
  "Create n peers, each with a monotically increasing port number.
  Then run (fun i) with the peer graph bound to initialize each peer,
  and i being the index of the peer being created."
  [n start-port fun]
  (doall
    (for [i (range n)]
        (let [p (peer (str "db/peer-" i)
                      {:port (+ start-port i)})]
          (with-peer-graph p
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
                    (make-edge root-id (make-node) :net))))]
    (is (= 1 (count (query strapper (q/path [:net]) 200))))
    (try
      (doall
        (for [p peers]
          (bootstrap p strap-url)))
      (Thread/sleep (* 2.1 BOOTSTRAP-RETRY-PERIOD))
      (let [all-peers (query strapper (q/path [:net :peer]))
            p-count (first (query (first peers) (q/count* (q/path [:net :peer])) 200))]
        (is (= n-peers (count all-peers)))
        (is (= N-BOOTSTRAP-PEERS p-count)))
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
                    (make-edge root-id (make-node) :net)))))

;(def con (get-connection (:manager (first peers)) strap-url))
(bootstrap (first peers) (plasma-url "localhost" 2234))
(peer-query con (-> (q/path [peer [:net :peer]])
                                  (q/choose N-BOOTSTRAP-PEERS)
                                  (q/project 'peer [:proxy :id]))
                              500)
  )

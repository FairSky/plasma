(ns test-utils
  (:use [plasma core operator connection]
        [clojure test stacktrace]
        [jiraph graph])
  (:require [lamina.core :as lamina]))

(def G (open-graph "test/db"))

(defn test-graph []
  (let [root-id (root-node)]
    (with-nodes! [net      :net
                  music    :music
                  synths   :synths
                  kick    {:label :kick  :score 0.8}
                  hat     {:label :hat   :score 0.3}
                  snare   {:label :snare :score 0.4}
                  bass    {:label :bass  :score 0.6}
                  sessions :sessions
                  take-six :take-six
                  red-pill :red-pill]
                 (make-edge root-id net       :net)
                 (make-edge root-id music     :music)
                 (make-edge music synths      :synths)
                 (make-edge synths bass       :synth)
                 (make-edge synths hat        :synth)
                 (make-edge synths kick       :synth)
                 (make-edge synths snare      :synth)
                 (make-edge root-id sessions  :sessions)
                 (make-edge sessions take-six :session)
                 (make-edge take-six kick     :synth)
                 (make-edge take-six bass     :synth)
                 (make-edge sessions red-pill :session)
                 (make-edge red-pill hat      :synth)
                 (make-edge red-pill snare    :synth)
                 (make-edge red-pill kick     :synth))))

(defn test-fixture [f]
  (with-graph G
    (clear-graph)
    (test-graph)
    (f)))

(defn close-peers
  [peers]
  (doseq [p peers]
    (close p)))


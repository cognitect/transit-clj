;; Copyright 2014 Rich Hickey. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS-IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns transit.check-transit
  (:require [cognitect.transit :as transit]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [transit.verify :as verify]
            [clojure.string :as str])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]))

(defn roundtrip [encoding]
  (prop/for-all [value gen/any-printable]
                (let [out (ByteArrayOutputStream. 2048)
                      writer (transit/writer out encoding)
                      _ (transit/write writer value)
                      in (ByteArrayInputStream. (.toByteArray out))
                      reader (transit/reader in encoding)]
                  (= value (transit/read reader)))))

(def record-name (gen/elements ["Foo" "Bar" "Baz"]))
(def ns-segments (gen/elements ["foo" "bar" "baz" "x" "y" "z" "a-b"]))

(def record-sym (gen/fmap (fn [[segs name]]
                            (symbol (if (empty? segs)
                                      name
                                      (str (str/join "." segs) "/" name))))
                          (gen/tuple (gen/vector ns-segments) record-name)))

(defn record-read-handler-returns-read-handler []
  (prop/for-all [sym transit.check-transit/record-sym]
    (let [ns-name (namespace sym)
          ns-sym (if ns-name (symbol ns-name) 'user)
          record-sym (symbol (name sym))]
      (binding [*ns* *ns*]
        (eval `(do (ns ~ns-sym)
                   (defrecord ~record-sym [a#])
                   (let [handler# (transit/record-read-handler
                                   (resolve (symbol (Compiler/munge (str '~ns-sym "." '~record-sym)))))]
                     (.fromRep handler# {:a 1}))))))))

(defn run-test [n property test-desc]
  (println "check:" test-desc)
  (let [result (tc/quick-check n property :max-size 50)
        color (if (:result result) :green :red)]
    (println (verify/with-style color (pr-str result)))))

(defn -main [& args]
  (binding [verify/*style* true]
    (let [n (Long/parseLong (first args))]
      (run-test 100 (record-read-handler-returns-read-handler)
                "record-read-handler returns ReadHandler")
      (doseq [t [:json :json-verbose :msgpack]]
        (run-test n (roundtrip t)
                  (format "%s encoding..." (name t)))))))

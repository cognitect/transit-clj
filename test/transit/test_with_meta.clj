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

(ns transit.test-with-meta
  (:require [clojure.test :refer [deftest is run-tests]]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(deftest test-basic-with-meta-json
  (let [out (ByteArrayOutputStream. 2000)
        w   (t/writer out :json {:transform t/write-meta})
        _   (t/write w (with-meta [1 2 3] {:foo 'bar}))
        in  (ByteArrayInputStream. (.toByteArray out))
        r   (t/reader in :json)
        x   (t/read r)]
    (is (= [1 2 3] x))
    (is (= {:foo 'bar} (meta x)))))

(deftest test-basic-with-meta-msgpack
  (let [out (ByteArrayOutputStream. 2000)
        w   (t/writer out :msgpack {:transform t/write-meta})
        _   (t/write w (with-meta [1 2 3] {:foo 'bar}))
        in  (ByteArrayInputStream. (.toByteArray out))
        r   (t/reader in :msgpack)
        x   (t/read r)]
    (is (= [1 2 3] x))
    (is (= {:foo 'bar} (meta x)))))

(deftest test-symbol-with-meta
  (let [out (ByteArrayOutputStream. 2000)
        w   (t/writer out :json {:transform t/write-meta})
        _   (t/write w (with-meta 'foo {:bar "baz"}))
        in  (ByteArrayInputStream. (.toByteArray out))
        r   (t/reader in :json)
        x   (t/read r)]
    (is (= 'foo x))
    (is (= {:bar "baz"} (meta x)))))

(deftest test-nested-with-meta
  (let [out (ByteArrayOutputStream. 2000)
        w   (t/writer out :json {:transform t/write-meta})
        _   (t/write w {:amap (with-meta [1 2 3] {:foo 'bar})})
        in  (ByteArrayInputStream. (.toByteArray out))
        r   (t/reader in :json)
        x   (t/read r)]
    (is (= [1 2 3] (:amap x)))
    (is (= {:foo 'bar} (-> x :amap meta)))))

(comment

  (run-tests)

  )
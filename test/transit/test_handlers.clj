(ns transit.test-handlers
  (:require [clojure.test :refer [deftest is run-tests]]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defrecord R [a])

(deftest test-no-default
  ;; no default handler: throws
  (is (thrown? Exception
        (let [out (ByteArrayOutputStream. 4096)
              writer (t/writer out :json)]
          (t/write writer (atom 1)))))

  ;; add default handler: succeeds
  (let [out (ByteArrayOutputStream. 4096)
        default-handler (t/write-handler
                          (fn [_] "atom")
                          (fn [a] @a))
        writer (t/writer out :json {:default-handler default-handler})]
    (t/write writer (atom 1))))

(comment
  (run-tests)
  )

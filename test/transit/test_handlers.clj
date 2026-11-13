(ns transit.test-handlers
  (:require [clojure.test :refer [deftest is run-tests]]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.msgpack.core MessageInsufficientBufferException MessagePack]))

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

(deftest test-read-list-with-declared-size-larger-than-data
  (let [out (ByteArrayOutputStream. 2000)
        packer (MessagePack/newDefaultPacker out)
        _ (doto packer
            (.packArrayHeader 2)
            (.packString "~#list")
            (.packArrayHeader Integer/MAX_VALUE)
            (.packLong 1)
            (.packLong 2)
            (.flush))
        in (ByteArrayInputStream. (.toByteArray out))
        r (t/reader in :msgpack)]
    (is (instance? MessageInsufficientBufferException
                   (try (t/read r)
                        (catch RuntimeException e (.getCause e)))))))

(comment
  (run-tests)
  )

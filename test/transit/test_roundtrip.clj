;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.test-roundtrip
  (:require [transit :as t]
            [clojure.java.io :as io]))

(defn -main [& args]
  (assert (first args) "-main requires an encoding argument")
  (let [type (keyword (first args))
        reader (t/reader System/in type)
        writer (t/writer System/out type)]
    (try
      (while true
        (t/write writer (t/read reader)))
      (catch Throwable e
        (let [fex (io/file (str "last-exception-" (System/nanoTime)))]
          (spit fex (.toString e)))))))

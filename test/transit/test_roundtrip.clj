;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.test-roundtrip
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io]))

(defn -main [& args]
  (assert (first args) "-main requires an encoding argument")
  (let [type (keyword (first args))
        reader (r/reader System/in type)
        writer (w/writer System/out type)]
    (try
      (while true
        (w/write writer (r/read reader)))
      (catch Throwable e
        (let [fex (io/file (str "last-exception-" (System/nanoTime)))]
          (spit fex (.toString e)))))))

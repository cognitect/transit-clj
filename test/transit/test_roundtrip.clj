(ns transit.test-roundtrip
  (:require [transit.read :as r]
            [transit.write :as w]))

(defn -main [& args]
  (assert (first args) "-main requires an encoding argument")
  (let [type (keyword (first args))
        reader (r/reader System/in type)
        writer (w/writer System/out type)]
    (try
      (while true
        (w/write writer (r/read reader)))
      (catch Throwable e
        ;; exit
        ))))

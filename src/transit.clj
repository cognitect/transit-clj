;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit
  (:require [transit.read :as r]
            [transit.write :as w]))

(comment

  (import [java.io File ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  (def out (ByteArrayOutputStream. 2000))

  (def w (w/writer out :json))

  (def w (w/writer out :msgpack))

  (w/write w "foo")
  (w/write w 10)
  (w/write w {:a-key 1 :b-key 2})
  (w/write w {"a" "1" "b" "2"})
  (w/write w {:a-key [1 2]})
  (w/write w #{1 2})
  (w/write w [{:a-key 1} {:a-key 2}])
  (w/write w [#{1 2} #{1 2}])
  (w/write w (int-array (range 10)))
  (w/write w {[:a :b] 2})

  (def in (ByteArrayInputStream. (.toByteArray out)))

  (def r (r/reader in :json))

  (def r (r/reader in :msgpack))

  (r/read r)

  ;; extensibility

  (defrecord Point [x y])

  (defrecord Circle [c r])

  (def ext-handlers
    {Point
     (w/make-handler (constantly "point")
                     (fn [p] (com.cognitect.transit.impl.TaggedValue. "array" [(.x p) (.y p)]))
                     (constantly nil))
     Circle
     (w/make-handler (constantly "circle")
                     (fn [c] (com.cognitect.transit.impl.TaggedValue. "array" [(.c c) (.r c)]))
                     (constantly nil))})

  (def ext-decoders
    {"point"
     (r/make-decoder (fn [[x y]] (prn "making a point") (Point. x y)))
     "circle"
     (r/make-decoder (fn [[c r]] (prn "making a circle") (Circle. c r)))})

  (def out (ByteArrayOutputStream. 2000))
  (def w (w/writer out :json {:handlers ext-handlers}))
  (w/write w (Point. 10 20))
  (w/write w (Circle. (Point. 10 20) 30))

  (def in (ByteArrayInputStream. (.toByteArray out)))
  (def r (r/reader in :json {:decoders ext-decoders}))
  (r/read r)
)
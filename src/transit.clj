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

  (def in (ByteArrayInputStream. (.toByteArray out)))

  (def r (r/reader in :json))

  (def r (r/reader in :msgpack))

  (r/read r)

  ;; extensibility

  (defrecord Point [x y])

  (defrecord Circle [c r])

  (extend-protocol transit.write/Handler
    Point
    (tag [_] "point")
    (rep [p] (transit.write/as-tag :array [(.x p) (.y p)] nil))
    (str-rep [_] nil)
    Circle
    (tag [_] "circle")
    (rep [c] (transit.write/as-tag :array [(.c c) (.r c)] nil))
    (str-rep [_] nil))

  (r/register-decode-fn "point" (fn [[x y]] (prn "making a point") (Point. x y)))

  (r/register-decode-fn "circle" (fn [[c r]] (prn "making a circle") (Circle. c r)))

  (w/write w (Point. 10 20))
  (w/write w (Circle. (Point. 10 20) 30))

)
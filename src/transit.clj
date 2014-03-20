(ns transit
  (:require [transit.read :as r]
            [transit.write :as w]))

(comment

  (import [java.io File ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  (def out (ByteArrayOutputStream. 2000))

  (def w (w/js-writer out))

  (def w (w/mp-writer out))

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

  (def r (r/js-reader in))

  (def r (r/mp-reader in))

  (r/read r)

  (defrecord Point [x y])

  (defrecord Circle [c r])

  (extend-protocol datomic.marshal.encode/Encoder
    Point
    (tag [_] "point")
    (rep [p] (datomic.marshal.encode/as-tag :array [(.x p) (.y p)]))
    Circle
    (tag [_] "circle")
    (rep [c] (datomic.marshal.encode/as-tag :array [(.c c) (.r c)])))

  (extend-protocol datomic.marshal.json/TagRep
    Point
    (tag [_] "point")
    (rep [p] (datomic.marshal.encode/as-tag :array [(.x p) (.y p)]))
    Circle
    (tag [_] "circle")
    (rep [c] (datomic.marshal.encode/as-tag :array [(.c c) (.r c)])))

  (enc/register-decode-fn "point" (fn [[x y]] (prn "making a point") (Point. x y)))

  (enc/register-decode-fn "circle" (fn [[c r]] (prn "making a circle") (Circle. c r)))

  (w/write w (Point. 10 20))
  (w/write w (Circle. (Point. 10 20) 30)))
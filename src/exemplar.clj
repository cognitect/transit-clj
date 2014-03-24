(ns exemplar
  (:require [transit.read :as r]
            [transit.write :as w]))

  (import [java.io File FileOutputStream ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  (defn range-centered-on [n]
    (range (- n 5) (+ n 6)))
  
  (defn write-exemplar [file-name & vals]
    (with-open [fos (FileOutputStream. (str file-name ".json"))]
      (let [jsw (w/js-writer fos)]
        (doseq [item vals] (w/write jsw item))))
    (with-open [fos (FileOutputStream. (str file-name ".mp"))]
      (let [jsw (w/mp-writer fos)]
        (doseq [item vals] (w/write jsw item)))))
    
  (write-exemplar "one-string" "hello transit")

  (apply write-exemplar "small-ints" (range-centered-on 0))

  (apply write-exemplar "large-ints"
    (concat 
     (range-centered-on 32768)
     (range-centered-on 65536)
     (range-centered-on 2147483648)
     (range-centered-on 4294967296)
     (range-centered-on 2305843009213693952)
     (range-centered-on 4611686018427387904)))

;;  (def w (w/js-writer out))
;;
;;  #_(def w (w/mp-writer out))
;;
;;  (w/write w "foo")
;;
;;  (write-range-centered-on w 2)
;;  (write-range-centered-on w 4)
;;
;;  (w/write w {:a-key 1 :b-key 2})
;;  (w/write w {"a" "1" "b" "2"})
;;  (w/write w {:a-key [1 2]})
;;  (w/write w #{1 2})
;;  (w/write w [{:a-key 1} {:a-key 2}])
;;  (w/write w [#{1 2} #{1 2}])
;;  (w/write w (int-array (range 10)))
;;
;;  (defrecord Point [x y])
;;
;;  (defrecord Circle [c r])
;;
;;(comment
;;  (def in (ByteArrayInputStream. (.toByteArray out)))
;;
;;  (def r (r/js-reader in))
;;
;;  (def r (r/mp-reader in))
;;
;;  (r/read r)
;;
;;  #_(extend-protocol datomic.marshal.encode/Encoder
;;    Point
;;    (tag [_] "point")
;;    (rep [p] (datomic.marshal.encode/as-tag :array [(.x p) (.y p)]))
;;    Circle
;;    (tag [_] "circle")
;;    (rep [c] (datomic.marshal.encode/as-tag :array [(.c c) (.r c)])))
;;
;;  #_(extend-protocol datomic.marshal.json/TagRep
;;    Point
;;    (tag [_] "point")
;;    (rep [p] (datomic.marshal.encode/as-tag :array [(.x p) (.y p)]))
;;    Circle
;;    (tag [_] "circle")
;;    (rep [c] (datomic.marshal.encode/as-tag :array [(.c c) (.r c)])))
;;
;;  #_(enc/register-decode-fn "point" (fn [[x y]] (prn "making a point") (Point. x y)))
;;
;;)
;;  #_(enc/register-decode-fn "circle" (fn [[c r]] (prn "making a circle") (Circle. c r)))
;;
;;  (w/write w (Point. 10 20))
;;  (w/write w (Circle. (Point. 10 20) 30))

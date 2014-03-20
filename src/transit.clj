(ns transit
  (:require [transit.read :as r]
            [transit.write :as w]))

(comment

  (require '[transit.generators :as gen])
  (require '[clojure.edn :as edn])
  (require '[clojure.data.fressian :as f])

  (import [java.io File ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  ;; take a seq of n forms, time writing/reading via edn, fressian and transit

  (defn now [] (. System (nanoTime)))
  (defn msecs [start end] (/ (double (- end start)) 1000000.0))

  (defn edn-rt
    [form]
    (let [start (now)
          str (pr-str form)
          mid (now)
          form2 (edn/read-string str)
          end (now)]
      {:form form2
       :write (msecs start mid)
       :read (msecs mid end)}))

  (defn fressian-rt
    [form]
    (let [start (now)
          bytes (f/write form)
          mid (now)
          form2 (f/read bytes)
          end (now)]
      {:form form2
       :write (msecs start mid)
       :read (msecs mid end)}))

  (defn transit-js-rt
    [form]
    (let [start (now)
          out (ByteArrayOutputStream. 10000)
          _ (w/write (w/js-writer out) form)
          mid (now)
          in (ByteArrayInputStream. (.toByteArray out))
          form2 (r/read (r/js-reader in))
          end (now)]
      {:form form2
       :write (msecs start mid)
       :read (msecs mid end)}))

  (defn transit-mp-rt
    [form]
    (let [start (now)
          out (ByteArrayOutputStream. 10000)
          _ (w/write (w/mp-writer out) form)
          mid (now)
          in (ByteArrayInputStream. (.toByteArray out))
          form2 (r/read (r/mp-reader in))
          end (now)]
      {:form form2
       :write (msecs start mid)
       :read (msecs mid end)}))


  (defn rt
    [form]
    {:edn (edn-rt form)
     :fressian (fressian-rt form)
     :transit-js (transit-js-rt form)
     :transit-mp (transit-mp-rt form)})

  (defn rt-summary
    [form]
    (let [res (rt form)]
      (clojure.pprint/pprint (into {} (map (fn [[k v]] [k (dissoc v :form)]) res)))))



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
;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
  (:import [com.cognitect.transit Handler Decoder
            TransitFactory TransitFactory$Format
            MapBuilder ListBuilder ArrayBuilder SetBuilder]
           [java.io InputStream OutputStream]))

;; writing

(set! *warn-on-reflection* true)

(defn transit-format
  [kw]
  (-> kw
      name
      str/upper-case
      TransitFactory$Format/valueOf))

(defn tagged-value
  ([tag rep] (tagged-value tag rep nil))
  ([tag rep str-rep] (TransitFactory/taggedValue tag rep str-rep)))

(defn nsed-name
  [^clojure.lang.Named kw-or-sym]
  (if-let [ns (.getNamespace kw-or-sym)]
    (str ns "/" (.getName kw-or-sym))
    (.getName kw-or-sym)))

(defn make-handler
  [tag-fn rep-fn str-rep-fn]
  (reify Handler
    (tag [_ o] (tag-fn o))
    (rep [_ o] (rep-fn o))
    (stringRep [_ o] (str-rep-fn o))))

(defn default-handlers
  []
  {
   java.util.List
   (reify Handler
     (tag [_ l] (if (seq? l) "list" "array"))
     (rep [_ l] (if (seq? l) (TransitFactory/taggedValue "array" l ) l))
     (stringRep [_ _] nil))

   clojure.lang.BigInt
   (reify Handler
     (tag [_ _] "i")
     (rep [_ bi] (biginteger bi))
     (stringRep [_ bi] (str bi)))

   clojure.lang.Keyword
   (reify Handler
     (tag [_ _] ":")
     (rep [_ kw] (nsed-name kw))
     (stringRep [_ kw] (nsed-name kw)))

   clojure.lang.Ratio
   (reify Handler
     (tag [_ _] "ratio")
     (rep [_ r] (TransitFactory/taggedValue "array" [(numerator r) (denominator r)]))
     (stringRep [_ _] nil))

   clojure.lang.Symbol
   (reify Handler
     (tag [_ _] "$")
     (rep [_ sym] (nsed-name sym))
     (stringRep [_ sym] (nsed-name sym)))
   })

(deftype Writer [w])

(defn writer
  ([out type] (writer out type {}))
  ([^OutputStream out type opts]
     (if (#{:json :msgpack} type)
       (let [handlers (merge (default-handlers) (:handlers opts))]
         (Writer. (TransitFactory/writer (transit-format type) out handlers (get opts :cache true))))
       (throw (ex-info "Type must be :json or :msgpack" {:type type})))))

(defn write [^Writer writer o] (.write ^com.cognitect.transit.Writer (.w writer) o))


;; reading

(defn make-decoder
  [decode-fn]
  (reify Decoder
    (decode [_ o] (decode-fn o))))

(defn default-decoders
  []
  {":"
   (reify Decoder
     (decode [_ o] (keyword o)))

   "$"
   (reify Decoder
     (decode [_ o] (symbol o)))

   "ratio"
   (reify Decoder
     (decode [_ o] (/ (.get ^java.util.List o 0)
                      (.get ^java.util.List o 1))))})

(defn map-builder
  []
  (reify MapBuilder
    (init [_] (transient {}))
    (init [_ ^int size] (transient {}))
    (add [_ mb k v] (assoc! mb k v))
    (^java.util.Map map [_ mb] (persistent! mb))))

(defn list-builder
  []
  (reify ListBuilder
    (init [_] (java.util.ArrayList.))
    (init [_ ^int size] (java.util.ArrayList. size))
    (add [_ lb item] (.add ^java.util.List lb item) lb)
    (^java.util.List list [_ lb]
      (or (seq lb) '())
      #_(apply list lb)
      )))

(defn set-builder
  []
  (reify SetBuilder
    (init [_] (transient #{}))
    (init [_ ^int size] (transient #{}))
    (add [_ sb item] (conj! sb item))
    (^java.util.Set set [_ sb] (persistent! sb))))

(defn array-builder
  []
  (reify ArrayBuilder
    (init [_] (transient []))
    (init [_ ^int size] (transient []))
    (add [_ ab item] (conj! ab item))
    (^java.util.List array [_ ab] (persistent! ab))))

(deftype Reader [r])

(defn reader
  ([in type] (reader in type {}))
  ([^InputStream in type opts]
     (if (#{:json :msgpack} type)
       (let [decoders (merge (default-decoders) (:decoders opts))]
         (Reader. (TransitFactory/reader (transit-format type)
                                         in
                                         decoders
                                         (map-builder)
                                         (list-builder)
                                         (array-builder)
                                         (set-builder))))
       (throw (ex-info "Type must be :json or :msgpack" {:type type})))))

(defn read [^Reader reader] (.read ^com.cognitect.transit.Reader (.r reader)))


(comment

  (require 'transit)
  (in-ns 'transit)

  (import [java.io File ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  (def out (ByteArrayOutputStream. 2000))

  (def w (writer out :json))
  (def w (writer out :json {:cache false}))

  (def w (writer out :msgpack))

  (write w "foo")
  (write w 10)
  (write w {:a-key 1 :b-key 2})
  (write w {"a" "1" "b" "2"})
  (write w {:a-key [1 2]})
  (write w #{1 2})
  (write w [{:a-key 1} {:a-key 2}])
  (write w [#{1 2} #{1 2}])
  (write w (int-array (range 10)))
  (write w {[:a :b] 2})

  (def in (ByteArrayInputStream. (.toByteArray out)))

  (def r (reader in :json))

  (def r (reader in :msgpack))

  (read r)
  (type (read r))

  ;; extensibility

  (defrecord Point [x y])

  (defrecord Circle [c r])

  (def ext-handlers
    {Point
     (make-handler (constantly "point")
                     (fn [p] (com.cognitect.transit.impl.TaggedValue. "array" [(.x p) (.y p)]))
                     (constantly nil))
     Circle
     (make-handler (constantly "circle")
                     (fn [c] (com.cognitect.transit.impl.TaggedValue. "array" [(.c c) (.r c)]))
                     (constantly nil))})

  (def ext-decoders
    {"point"
     (make-decoder (fn [[x y]] (prn "making a point") (Point. x y)))
     "circle"
     (make-decoder (fn [[c r]] (prn "making a circle") (Circle. c r)))})

  (def out (ByteArrayOutputStream. 2000))
  (def w (writer out :json {:handlers ext-handlers}))
  (write w (Point. 10 20))
  (write w (Circle. (Point. 10 20) 30))

  (def in (ByteArrayInputStream. (.toByteArray out)))
  (def r (reader in :json {:decoders ext-decoders}))
  (read r)
)

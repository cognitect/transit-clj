;; Copyright 2014 Rich Hickey. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS-IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns cognitect.transit
  "An implementation of the transit-format for Clojure built
   on top of the transit-java library."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
  (:import [com.cognitect.transit WriteHandler ReadHandler
            ArrayReader TransitFactory TransitFactory$Format]
           [com.cognitect.transit.impl ReaderSPI
            MapBuilder ArrayBuilder]
           [java.io InputStream OutputStream]))

;; writing

(set! *warn-on-reflection* true)

(defn- transit-format
  "Converts a keyword to a TransitFactory$Format value."
  [kw]
  (TransitFactory$Format/valueOf 
   (str/join "_" (-> kw
                     name
                     str/upper-case
                     (str/split #"-")))))

(defn tagged-value
  "Creates a TaggedValue object."
  [tag rep] (TransitFactory/taggedValue tag rep))

(defn nsed-name
  "Convert a keyword or symbol to a string in
   namespace/name format."
  [^clojure.lang.Named kw-or-sym]
  (if-let [ns (.getNamespace kw-or-sym)]
    (str ns "/" (.getName kw-or-sym))
    (.getName kw-or-sym)))

(defn write-handler
  "Creates a transit WriteHandler whose tag, rep,
   stringRep, and verboseWriteHandler methods
   invoke the provided fns."
  ([tag-fn rep-fn]
     (write-handler tag-fn rep-fn nil nil))
  ([tag-fn rep-fn str-rep-fn]
     (write-handler tag-fn rep-fn str-rep-fn nil))
  ([tag-fn rep-fn str-rep-fn verbose-handler-fn]
     (reify WriteHandler
       (getTag [_ o] (tag-fn o))
       (getRep [_ o] (rep-fn o))
       (getStringRep [_ o] (when str-rep-fn (str-rep-fn o)))
       (getVerboseHandler [_] (when verbose-handler-fn (verbose-handler-fn))))))

(defn default-write-handlers
  "Returns a map of default WriteHandlers for
   Clojure types. Java types are handled
   by the default WriteHandlers provided by the
   transit-java library."
  []
  {
   java.util.List
   (reify WriteHandler
     (getTag [_ l] (if (seq? l) "list" "array"))
     (getRep [_ l] (if (seq? l) (TransitFactory/taggedValue "array" l ) l))
     (getStringRep [_ _] nil)
     (getVerboseHandler [_] nil))

   clojure.lang.BigInt
   (reify WriteHandler
     (getTag [_ _] "n")
     (getRep [_ bi] (str (biginteger bi)))
     (getStringRep [this bi] (.getRep this bi))
     (getVerboseHandler [_] nil))

   clojure.lang.Keyword
   (reify WriteHandler
     (getTag [_ _] ":")
     (getRep [_ kw] (nsed-name kw))
     (getStringRep [_ kw] (nsed-name kw))
     (getVerboseHandler [_] nil))

   clojure.lang.Ratio
   (reify WriteHandler
     (getTag [_ _] "ratio")
     (getRep [_ r] (TransitFactory/taggedValue "array" [(numerator r) (denominator r)]))
     (getStringRep [_ _] nil)
     (getVerboseHandler [_] nil))

   clojure.lang.Symbol
   (reify WriteHandler
     (getTag [_ _] "$")
     (getRep [_ sym] (nsed-name sym))
     (getStringRep [_ sym] (nsed-name sym))
     (getVerboseHandler [_] nil))
   })

(deftype Writer [w])

(defn writer
  "Creates a writer over the privided destination `out` using
   the specified format, one of: :msgpack, :json or :json-verbose.

   An optional opts map may be passed. Supported options are:

   :Handlers - a map of types to WriteHandler instances, they are merged
   with the default-handlers and then with the default handlers
   provided by transit-java."
  ([out type] (writer out type {}))
  ([^OutputStream out type opts]
     (if (#{:json :json-verbose :msgpack} type)
       (let [handlers (merge (default-write-handlers) (:handlers opts))]
         (Writer. (TransitFactory/writer (transit-format type) out handlers)))
       (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type})))))

(defn write
  "Writes a value to a transit writer."
  [^Writer writer o]
  (.write ^com.cognitect.transit.Writer (.w writer) o))


;; reading

(defn read-handler
  "Creates a transit ReadHandler whose fromRep
   method invokes the provided fn. Optionally,
   can provide from-array-rep or from-map-rep
   fn which parser will use for incremental
   decoding of types with array or map representations,
   respectively."
  ([from-rep] (read-handler from-rep nil nil))
  ([from-rep from-array-rep from-map-rep]
     (reify ReadHandler
       (fromRep [_ o] (from-rep o))
       (fromArrayRep [_] (when from-array-rep (from-array-rep)))
       (fromMapRep [_] (when from-map-rep (from-map-rep))))))

(defn default-read-handlers
  "Returns a map of default ReadHandlers for
   Clojure types. Java types are handled
   by the default ReadHandlers provided by the
   transit-java library."
  []
  {":"
   (reify ReadHandler
     (fromRep [_ o] (keyword o))
     (fromArrayRep [_] nil)
     (fromMapRep [_] nil))

   "$"
   (reify ReadHandler
     (fromRep [_ o] (symbol o))
     (fromArrayRep [_] nil)
     (fromMapRep [_] nil))

   "ratio"
   (reify ReadHandler
     (fromRep [_ o] (/ (.get ^java.util.List o 0)
                       (.get ^java.util.List o 1)))
     (fromArrayRep [_] nil)
     (fromMapRep [_] nil))

   "n"
   (reify ReadHandler
     (fromRep [_ o] (clojure.lang.BigInt/fromBigInteger
                     (BigInteger. ^String o)))
     (fromArrayRep [_] nil)
     (fromMapRep [_] nil))

   "set"
   (reify ReadHandler
     (fromRep [_ o] o)
     (fromArrayRep [_]
       (reify ArrayReader
         (init [_] (transient #{}))
         (init [_ ^int size] (transient #{}))
         (add [_ s item] (conj! s item))
         (complete [_ s] (persistent! s))))
     (fromMapRep [_] nil))

   "list"
   (reify ReadHandler
     (fromRep [_ o] o)
     (fromArrayRep [_]
       (reify ArrayReader
         (init [_] (java.util.ArrayList.))
         (init [_ ^int size] (java.util.ArrayList. size))
         (add [_ l item] (.add ^java.util.List l item) l)
         (complete [_ l] (or (seq l) '()))))
     (fromMapRep [_] nil))

   "cmap"
   (reify ReadHandler
     (fromRep [_ o] o)
     (fromArrayRep [_]
       (let [next-key (atom nil)]
         (reify ArrayReader
           (init [_] (transient {}))
           (init [_ ^int size] (transient {}))
           (add [_ m item]
             (if-let [k @next-key]
               (do
                 (reset! next-key nil)
                 (assoc! m k item))
               (do
                 (reset! next-key item)
                 m)))
           (complete [_ m] (persistent! m)))))
     (fromMapRep [_] nil))})

(defn map-builder
  "Creates a MapBuilder that makes Clojure-
   compatible maps."
  []
  (reify MapBuilder
    (init [_] (transient {}))
    (init [_ ^int size] (transient {}))
    (add [_ m k v] (assoc! m k v))
    (^java.util.Map complete [_ m] (persistent! m))))

(defn array-builder
  []
  "Creates an ArrayBuilder that makes Clojure-
   compatible lists."
  (reify ArrayBuilder
    (init [_] (transient []))
    (init [_ ^int size] (transient []))
    (add [_ v item] (conj! v item))
    (^java.util.List complete [_ v] (persistent! v))))

(deftype Reader [r])

(defn reader
  "Creates a reader over the provided source `in` using
   the specified format, one of: :msgpack, :json or :json-verbose.

   An optional opts map may be passed. Supported options are:

   :handlers - a map of tags to ReadHandler instances, they are merged
   with the Clojure default-read-handlers and then with the default ReadHandlers
   provided by transit-java.

   :default-handler - an instance of DefaultReadHandler, used to process
   transit encoded values for which there is no other ReadHandler; if
   :default-handler is not specified, non-readable values are returned
   as TaggedValues."
  ([in type] (reader in type {}))
  ([^InputStream in type opts]
     (if (#{:json :json-verbose :msgpack} type)
       (let [handlers (merge (default-read-handlers) (:handlers opts))
             default-handler (:default-handler opts)
             reader (TransitFactory/reader (transit-format type)
                                           in
                                           handlers
                                           default-handler)]
         (Reader. (.setBuilders ^ReaderSPI reader
                                (map-builder)
                                (array-builder))))
       (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type})))))

(defn read
  "Reads a value from a reader."
  [^Reader reader]
  (.read ^com.cognitect.transit.Reader (.r reader)))

(defn record-write-handler
  "Creates a WriteHandler for a record type"
  [^Class type]
  (reify WriteHandler
    (getTag [_ _] (.getName type))
    (getRep [_ rec] (tagged-value "map" rec))
    (getStringRep [_ _] nil)
    (getVerboseHandler [_] nil)))

(defn record-write-handlers
  "Creates a map of record types to WriteHandlers"
  [& types]
  (reduce (fn [h t] (assoc h t (record-write-handler t)))
          {}
          types))

(defn record-read-handler
  "Creates a ReadHandler for a record type"
  [^Class type]
  (let [type-name (str/split (.getName type) #"\.")
        map-ctor (-> (str (str/join "." (butlast type-name)) "/map->" (last type-name))
                     symbol
                     resolve)]
    (reify ReadHandler
      (fromRep [_ m] (map-ctor m)))))

(defn record-read-handlers
  "Creates a map of record type tags to ReadHandlers"
  [& types]
  (reduce (fn [d ^Class t] (assoc d (.getName t) (record-read-handler t)))
          {}
          types))

(comment

  (require 'cognitect.transit)
  (in-ns 'cognitect.transit)

  (import [java.io File ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

  (def out (ByteArrayOutputStream. 2000))

  (def w (writer out :json))
  (def w (writer out :json-verbose))

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
  (write w [123N])
  (write w 1/3)

  (def in (ByteArrayInputStream. (.toByteArray out)))

  (def r (reader in :json))

  (def r (reader in :msgpack))

  (read r)
  (type (read r))

  ;; extensibility

  (defrecord Point [x y])

  (defrecord Circle [c r])

  (def ext-write-handlers
    {Point
     (write-handler (constantly "point")
                    (fn [p] [(.x p) (.y p)])
                    (constantly nil))
     Circle
     (write-handler (constantly "circle")
                    (fn [c] [(.c c) (.r c)])
                    (constantly nil))})

  (def ext-read-handlers
    {"point"
     (read-handler (fn [[x y]] (prn "making a point") (Point. x y)))
     "circle"
     (read-handler (fn [[c r]] (prn "making a circle") (Circle. c r)))})

  (def ext-write-handlers
    (record-write-handlers Point Circle))

  (def ext-read-handlers
    (record-read-handlers Point Circle))

  (def out (ByteArrayOutputStream. 2000))
  (def w (writer out :json {:handlers ext-write-handlers}))
  (write w (Point. 10 20))
  (write w (Circle. (Point. 10 20) 30))

  (def in (ByteArrayInputStream. (.toByteArray out)))
  (def r (reader in :json {:handlers ext-read-handlers}))
  (read r)
  )

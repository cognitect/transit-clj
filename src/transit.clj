;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit
  "An implementation of the transit-format for Clojure built
   on top of the transit-java library."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
  (:import [com.cognitect.transit Handler Decoder
            TransitFactory TransitFactory$Format
            MapBuilder ListBuilder ArrayBuilder SetBuilder]
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
  ([tag rep] (tagged-value tag rep nil))
  ([tag rep str-rep] (TransitFactory/taggedValue tag rep str-rep)))

(defn nsed-name
  "Convert a keyword or symbol to a string in
   namespace/name format."
  [^clojure.lang.Named kw-or-sym]
  (if-let [ns (.getNamespace kw-or-sym)]
    (str ns "/" (.getName kw-or-sym))
    (.getName kw-or-sym)))

(defn make-handler
  "Creates a transit Handler whose tag, rep,
   stringRep, and verboseHandler methods
   invoke the provided fns."
  ([tag-fn rep-fn]
     (make-handler tag-fn rep-fn nil nil))
  ([tag-fn rep-fn str-rep-fn]
     (make-handler tag-fn rep-fn str-rep-fn nil))
  ([tag-fn rep-fn str-rep-fn verbose-handler-fn]
     (reify Handler
       (getTag [_ o] (tag-fn o))
       (getRep [_ o] (rep-fn o))
       (getStringRep [_ o] (when str-rep-fn (str-rep-fn o)))
       (getVerboseHandler [_] (when verbose-handler-fn (verbose-handler-fn))))))

(defn default-handlers
  "Returns a map of default Handlers for
   Clojure types. Java types are handled
   by the default Handlers provided by the
   transit-java library."
  []
  {
   java.util.List
   (reify Handler
     (getTag [_ l] (if (seq? l) "list" "array"))
     (getRep [_ l] (if (seq? l) (TransitFactory/taggedValue "array" l ) l))
     (getStringRep [_ _] nil)
     (getVerboseHandler [_] nil))

   clojure.lang.BigInt
   (reify Handler
     (getTag [_ _] "n")
     (getRep [_ bi] (str (biginteger bi)))
     (getStringRep [this bi] (.getRep this bi))
     (getVerboseHandler [_] nil))

   clojure.lang.Keyword
   (reify Handler
     (getTag [_ _] ":")
     (getRep [_ kw] (nsed-name kw))
     (getStringRep [_ kw] (nsed-name kw))
     (getVerboseHandler [_] nil))

   clojure.lang.Ratio
   (reify Handler
     (getTag [_ _] "ratio")
     (getRep [_ r] (TransitFactory/taggedValue "array" [(numerator r) (denominator r)]))
     (getStringRep [_ _] nil)
     (getVerboseHandler [_] nil))

   clojure.lang.Symbol
   (reify Handler
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

   :Handlers - a map of types to Handler instances, they are merged
   with the default-handlers and then with the default handlers
   provided by transit-java."
  ([out type] (writer out type {}))
  ([^OutputStream out type opts]
     (if (#{:json :json-verbose :msgpack} type)
       (let [handlers (merge (default-handlers) (:handlers opts))]
         (Writer. (TransitFactory/writer (transit-format type) out handlers)))
       (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type})))))

(defn write
  "Writes a value to a transit writer."
  [^Writer writer o]
  (.write ^com.cognitect.transit.Writer (.w writer) o))


;; reading

(defn make-decoder
  "Creates a transit Decoder whose decode
   method invokes the provided fn."
  [decode-fn]
  (reify Decoder
    (decode [_ o] (decode-fn o))))

(defn default-decoders
  "Returns a map of default Decoders for
   Clojure types. Java types are handled
   by the default Decoders provided by the
   transit-java library."
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
                      (.get ^java.util.List o 1))))

   "n"
   (reify Decoder
     (decode [_ o] (clojure.lang.BigInt/fromBigInteger
                    (BigInteger. ^String o))))})

(defn map-builder
  "Creates a MapBuilder that makes Clojure-
   compatible maps."
  []
  (reify MapBuilder
    (init [_] (transient {}))
    (init [_ ^int size] (transient {}))
    (add [_ mb k v] (assoc! mb k v))
    (^java.util.Map map [_ mb] (persistent! mb))))

(defn list-builder
  []
  "Creates a ListBuilder that makes Clojure-
   compatible list."
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
  "Creates a SetBuilder that makes Clojure-
   compatible sets."
  (reify SetBuilder
    (init [_] (transient #{}))
    (init [_ ^int size] (transient #{}))
    (add [_ sb item] (conj! sb item))
    (^java.util.Set set [_ sb] (persistent! sb))))

(defn array-builder
  []
  "Creates an ArrayBuilder that makes Clojure-
   compatible lists."
  (reify ArrayBuilder
    (init [_] (transient []))
    (init [_ ^int size] (transient []))
    (add [_ ab item] (conj! ab item))
    (^java.util.List array [_ ab] (persistent! ab))))

(deftype Reader [r])

(defn reader
  "Creates a reader over the provided source `in` using
   the specified format, one of: :msgpack, :json or :json-verbose.

   An optional opts map may be passed. Supported options are:

   :decoders - a map of tags to Decoder instances, they are merged
   with the Clojure default-decoders and then with the default decoders
   provided by transit-java.

   :default-decoder - an instance of DefaultDecoder, used to process
   transit encoded values for which there is no other decoder; if
   :default-decoder is not specified, non-decodable values are returned
   as TaggedValues; if :default-decoder is set to nil, non-decodable values
   will raise an exception."
  ([in type] (reader in type {}))
  ([^InputStream in type opts]
     (if (#{:json :json-verbose :msgpack} type)
       (let [decoders (merge (default-decoders) (:decoders opts))
             default-decoder (or (:default-decoder opts)
                                 (TransitFactory/defaultDefaultDecoder))]
         (Reader. (TransitFactory/reader (transit-format type)
                                         in
                                         decoders
                                         default-decoder
                                         (map-builder)
                                         (list-builder)
                                         (array-builder)
                                         (set-builder))))
       (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type})))))

(defn read
  "Reads a value from a reader."
  [^Reader reader]
  (.read ^com.cognitect.transit.Reader (.r reader)))


(comment

  (require 'transit)
  (in-ns 'transit)

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

  (def ext-handlers
    {Point
     (make-handler (constantly "point")
                   (fn [p] [(.x p) (.y p)])
                   (constantly nil))
     Circle
     (make-handler (constantly "circle")
                   (fn [c] [(.c c) (.r c)])
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

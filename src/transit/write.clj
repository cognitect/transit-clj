;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.write
  (:require [clojure.string :as str])
  (:import [com.cognitect.transit Handler TransitFactory TransitFactory$Format]
           [java.io InputStream OutputStream EOFException OutputStreamWriter]))

(set! *warn-on-reflection* true)

(defn make-handler
  [tag-fn rep-fn str-rep-fn]
  (reify Handler
    (tag [_ o] (tag-fn o))
    (rep [_ o] (rep-fn o))
    (stringRep [_ o] (str-rep-fn o))))

(defn nsed-name
  [^clojure.lang.Named kw-or-sym]
  (if-let [ns (.getNamespace kw-or-sym)]
    (str ns "/" (.getName kw-or-sym))
    (.getName kw-or-sym)))

(def default-handlers
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
       (let [handlers (merge default-handlers (:handlers opts))]
         (Writer. (TransitFactory/writer (-> type
                                             name
                                             str/upper-case
                                             TransitFactory$Format/valueOf)
                                         out
                                         handlers)))
       (throw (ex-info "Type must be :json or :msgpack" {:type type})))))

(defn write [^Writer writer o] (.write ^com.cognitect.transit.Writer (.w writer) o))



;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.read
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
  (:import [com.cognitect.transit Decoder TransitFactory TransitFactory$Format]
           [java.io InputStream]))

(set! *warn-on-reflection* true)

(defn make-decoder
  [decode-fn]
  (reify Decoder
    (decode [_ o] (decode-fn o))))

(def default-decoders
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

(deftype Reader [r])

(defn reader
  ([in type] (reader in type {}))
  ([^InputStream in type opts]
     (if (#{:json :msgpack} type)
       (let [decoders (merge default-decoders (:decoders opts))]
         (Reader. (TransitFactory/reader (-> type
                                             name
                                             str/upper-case
                                             TransitFactory$Format/valueOf)
                                         in
                                         decoders)))
       (throw (ex-info "Type must be :json or :msgpack" {:type type})))))


(defn read [^Reader reader] (.read ^com.cognitect.transit.Reader (.r reader)))


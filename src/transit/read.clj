;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.read
  (:refer-clojure :exclude [read])
  (:require [transit.write :as w]
            [clojure.edn :as edn])
  (:import [com.fasterxml.jackson.core
            JsonFactory JsonParser JsonToken JsonParseException]
           org.msgpack.MessagePack
           [org.msgpack.unpacker Unpacker MessagePackUnpacker]
           [org.msgpack.type Value MapValue ArrayValue RawValue ValueType]
           [org.apache.commons.codec.binary Base64]
           [java.io InputStream OutputStream EOFException]))

(set! *warn-on-reflection* true)

(defn read-int-str
  [s]
  (let [o (edn/read-string s)]
    (when (number? o) o)))

(def decode-fns (atom {":" #(keyword %)
                       "?" #(Boolean. ^String %)
                       "b" #(Base64/decodeBase64 ^bytes %)
                       "_" (fn [_] nil)
                       "i" #(try
                              (Long/parseLong %)
                              (catch NumberFormatException _ (read-int-str %)))
                       "d" #(Double. ^String %)
                       "f" #(java.math.BigDecimal. ^String %)
                       "t" #(if (string? %)
                              (java.util.Date. ^String %)
                              (java.util.Date. ^long %))
                       "u" #(if (string? %)
                              (java.util.UUID/fromString %)
                              (java.util.UUID. (first %) (second %)))
                       "r" #(java.net.URI. %)
                       "$" #(symbol %)
                       "set" #(reduce (fn [s v] (conj s v)) #{} %)
                       "list" #(reverse (into '() %))}))

(defn register-decode-fn
  [tag fn]
  (swap! decode-fns assoc tag fn))

(defn decode-fn
  [tag]
  ;;(prn "decode" tag rep)
  (@decode-fns tag))

(defprotocol ReadCache
  (cache-read [cache str as-map-key]))

(defn parse-str
  [s]
  ;;(prn "parse-str before" s)
  (let [res (if (and (string? s) (> (.length ^String s) 1))
              ;; any way to use w/ESC, et al?
              (case (.charAt ^String s 0)
                \~
                (case (.charAt ^String s 1)
                  \~ (subs s 1) ;; w/ESC
                  \^ (subs s 1) ;; w/SUB
                  \` (subs s 1) ;; w/RESERVED
                  \# s          ;; w/TAG
                  (if-let [decode-fn (decode-fn (subs s 1 2))]
                    (decode-fn (subs s 2))
                    s))
                s)
              s)]
    ;;(prn "parse-str after" res)
    res))

(defn parse-tagged-map
  [^java.util.Map m]
  (let [entries (.entrySet m)
        iter (.iterator entries)
        entry (when (.hasNext iter) (.next iter))
        key (when entry (.getKey ^java.util.Map$Entry entry))]
    (if (and entry (string? key) (> (.length ^String key) 1) (= w/TAG ^Character (.charAt ^String key 1)))
      (if-let [decode-fn (decode-fn (subs key 2))]
        (decode-fn (.getValue ^java.util.Map$Entry entry))
        m)
      m)))

(defn cache-code?
  [^String s]
  (= w/SUB ^Character (.charAt s 0)))

(defn code->idx
  [^String s]
  (- (byte ^Character (.charAt s 1)) w/BASE_CHAR_IDX))

(deftype ReadCacheImpl [^:unsynchronized-mutable idx cache]
  ReadCache
  (cache-read [_ str as-map-key]
    ;;(prn "cache read before" idx str)
    (let [res (if (and str (not (zero? (.length ^String str))))
                (if (w/cacheable? str as-map-key)
                  (do 
                    (when (= idx (dec w/MAX_CACHE_ENTRIES))
                      (set! idx 0))
                    (aset ^objects cache idx (parse-str str))
                    (set! idx (inc idx))
                    str)
                  (if (cache-code? str)
                    (aget ^objects cache (code->idx str))
                    str))
                str)]
      ;;(prn "cache read after" idx res)
      res)))

(defn read-cache [] (ReadCacheImpl. 0 (make-array Object w/MAX_CACHE_ENTRIES)))

(defprotocol Parser
  (parse [p cache])
  (parse-val [p as-map-key cache])
  (parse-map [p as-map-key cache])
  (parse-array [p as-map-key cache]))

(extend-protocol Parser
  JsonParser
  (parse [^JsonParser jp cache]
    (when (.nextToken jp) (parse-val jp false cache)))

  (parse-val [^JsonParser jp as-map-key cache]
    ;;(prn "parse-val" (.getCurrentToken jp))
    (condp = (.getCurrentToken jp)
      JsonToken/START_OBJECT
      (parse-tagged-map (parse-map jp as-map-key cache))
      JsonToken/START_ARRAY
      (parse-array jp as-map-key cache)
      JsonToken/FIELD_NAME
      (parse-str (cache-read cache (.getText jp) as-map-key))
      JsonToken/VALUE_STRING
      (parse-str (cache-read cache (.getText jp) as-map-key))
      JsonToken/VALUE_NUMBER_INT
      (try 
        (.getLongValue jp) ;; always read as long, coerce to string if too big
        (catch JsonParseException _ (read-int-str (.getText jp))))
      JsonToken/VALUE_NUMBER_FLOAT
      (.getDoubleValue jp) ;; always read as double
      JsonToken/VALUE_TRUE
      (.getBooleanValue jp)
      JsonToken/VALUE_FALSE
      (.getBooleanValue jp)
      JsonToken/VALUE_NULL
      nil))

  (parse-map [^JsonParser jp _ cache]
    (persistent!
     (let [res (transient {})]
       (while (not= (.nextToken jp) JsonToken/END_OBJECT)
         (let [k (parse-val jp true cache)
               _ (.nextToken jp)
               v (parse-val jp false cache)]
           (assoc! res k v)))
       res)))

  (parse-array [^JsonParser jp _ cache]
    (persistent!
     (let [res (transient [])]
       (while (not= (.nextToken jp) JsonToken/END_ARRAY) 
         (conj! res (parse-val jp false cache)))
       res))))

(extend-protocol Parser
  MessagePackUnpacker
  (parse [^MessagePackUnpacker mup cache] (parse-val mup false cache))

  (parse-val [^MessagePackUnpacker mup as-map-key cache]
    (try 
      (condp = (.getNextType mup)
        ValueType/MAP
        (parse-tagged-map (parse-map mup as-map-key cache))
        ValueType/ARRAY
        (parse-array mup as-map-key cache)
        ValueType/RAW
        (parse-str (cache-read cache
                               (-> mup .readValue .asRawValue .getString)
                               as-map-key))
        ValueType/INTEGER
        (-> mup .readValue .asIntegerValue .getLong) ;; always read as long
        ValueType/FLOAT
        (-> mup .readValue .asFloatValue .getDouble) ;; always read as double
        ValueType/BOOLEAN
        (-> mup .readValue .asBooleanValue .getBoolean)
        ValueType/NIL
        (.readNil mup))
      (catch EOFException e)))

  (parse-map [^MessagePackUnpacker mup _ cache]
    (persistent!
     (let [res (transient {})]
       (dotimes [_ (.readMapBegin mup)]
         (assoc! res (parse-val mup true cache) (parse-val mup false cache)))
       (.readMapEnd mup false)
       res)))

  (parse-array
    [^MessagePackUnpacker mup _ cache]
    (persistent! 
     (let [res (transient [])]
       (dotimes [_ (.readArrayBegin mup)]
         (conj! res (parse-val mup false cache)))
       (.readArrayEnd mup false)
       res))))

(deftype Reader [r])

(defn js-reader [^InputStream stm]
  (Reader. (.createParser (JsonFactory.) stm)))

(defn mp-reader [^InputStream stm]
  (Reader. (.createUnpacker (MessagePack.) stm)))

(defn read [^Reader reader]
  (parse (.r reader) (read-cache)))


(ns transit.roundtrip
  (:require [transit.read :as r]
            [transit.write :as w]
            [transit.generators :as gen]
            [clojure.edn :as edn]
            [clojure.data.fressian :as f])
  (:import [java.io File ByteArrayInputStream ByteArrayOutputStream EOFException]
           [com.fasterxml.jackson.core
            JsonFactory JsonParser JsonToken JsonParseException]
           org.msgpack.MessagePack
           [org.msgpack.unpacker Unpacker MessagePackUnpacker]
           [org.msgpack.type Value MapValue ArrayValue RawValue ValueType]))

(defn now [] (. System (nanoTime)))
(defn msecs [start end] (/ (double (- end start)) 1000000.0))

(defn rt
  [write-fn read-fn]
  (fn [form]
    (let [start (now)
          tmp (write-fn form)
          mid (now)
          form2 (read-fn tmp)
          end (now)]
      {:form form2
       :same (= form form2)
       :write (msecs start mid)
       :read (msecs mid end)})))

(defn edn-rt
  [form]
  ((rt pr-str edn/read-string) form))

(defn fressian-rt
  [form]
  ((rt f/write f/read) form))

(defn transit-writer
  [type]
  (fn [form]
    (let [out (ByteArrayOutputStream. 10000)
          w (w/writer out type)]
      (w/write w form)
      (.toByteArray out))))

(defn transit-reader
  [type]
  (fn [bytes]
    (let [r (r/reader (ByteArrayInputStream. bytes) type)]
      (r/read r))))

(defn transit-js-rt
  [form]
  ((rt (transit-writer :json) (transit-reader :json)) form))

(defn transit-mp-rt
  [form]
  ((rt (transit-writer :msgpack) (transit-reader :msgpack)) form))

(defn fake-js-reader
  [bytes]
  (let [jp ^JsonParser (.createJsonParser (JsonFactory.) (ByteArrayInputStream. bytes))]
    (while (.nextToken jp)
      (when (= (.getCurrentToken jp)
               JsonToken/VALUE_STRING)
        (.getText jp)))
    nil))

(defn fake-mp-reader
  [bytes]
  (let [mup ^MessagePackUnpacker (.createUnpacker (MessagePack.) (ByteArrayInputStream. bytes))]
    (try
      (while (.getNextType mup)
        (.readValue mup))
      (catch EOFException e))
    nil))

(defn transit-js-rt-fake-read
  [form]
  ((rt (transit-writer :json) fake-js-reader) form))

(defn transit-mp-rt-fake-read
  [form]
  ((rt (transit-writer :msgpack) fake-mp-reader) form))

(defn rt-raw
  [form]
  {:edn (edn-rt form)
   :fressian (fressian-rt form)
   :transit-js (transit-js-rt form)
   :transit-mp (transit-mp-rt form)
   ;;:transit-js-fake-read (transit-js-rt-fake-read form)
   ;;:transit-mp-fake-read (transit-mp-rt-fake-read form)
   })

(defn rt-summary
  [form]
  (let [res (rt-raw form)]
    (into {} (map (fn [[k v]] [k (dissoc v :form)]) res))))

(defn rt-summary-warm
  [n form]
  (dotimes [i n]
    (prn i)
    (rt-summary form))
  (rt-summary form))

(comment


)



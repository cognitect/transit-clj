;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.write
  (:import [com.fasterxml.jackson.core
            JsonGenerator JsonFactory JsonEncoding JsonParser JsonToken
            JsonParseException]
           org.msgpack.MessagePack
           org.msgpack.packer.Packer
           [org.apache.commons.codec.binary Base64]
           [java.io InputStream OutputStream EOFException OutputStreamWriter]))

(set! *warn-on-reflection* true)

(declare marshal)

(def ESC "~")
(def SUB "^")
(def TAG "#")
(def RESERVED "`")
(def ESC_TAG "~#")

(def MIN_SIZE_CACHEABLE 3)
(def MAX_CACHE_ENTRIES 94)
(def BASE_CHAR_IDX 33)

(defn cacheable?
  [^String str as-map-key]
  (and (> (.length str) MIN_SIZE_CACHEABLE)
       (or as-map-key
           (and (= ESC (subs str 0 1))
                (let [c (subs str 1 2)]
                  (or (= ":" c)
                      (= "$" c)
                      (= "#" c)))))))

(defn idx->code
  [i]
  (str SUB (char (+ i BASE_CHAR_IDX))))

(defprotocol WriteCache
  (cache-write [cache str as-map-key]))

(deftype WriteCacheImpl [^:unsynchronized-mutable idx
                         ^:unsynchronized-mutable cache]
  WriteCache
  (cache-write [_ str as-map-key]
    ;;(prn "cache write before" idx cache s)
    (let [res (if (and str (cacheable? str as-map-key))
                (if-let [val (get cache str)]
                  val
                  (do
                    (when (= idx MAX_CACHE_ENTRIES)
                      (set! idx 0)
                      (set! cache {}))
                    (set! cache (assoc cache str (idx->code idx)))
                    (set! idx (inc idx))
                    str))
                str)]
      ;;(prn "cache write after" idx cache res)
      res)))

(defn write-cache [] (WriteCacheImpl. 0 {}))

(defn escape
  [^String s]
  (if (> (.length s) 0)
    (let [c (subs s 0 1)]
      (cond (and (= RESERVED c) (= ESC (subs s 1 2)))
            (subs s 1)

            (or (= ESC c) (= SUB c) (= RESERVED c))
            (str ESC s)

            :else s))))

(defn nsed-name
  [^clojure.lang.Named kw-or-sym]
  (if-let [ns (.getNamespace kw-or-sym)]
    (str ns "/" (.getName kw-or-sym))
    (.getName kw-or-sym)))

(defprotocol Flushable
  (flush-stream [stm]))

(defprotocol Emitter
  (emit-nil [em as-map-key cache])
  (emit-string [em prefix tag s as-map-key cache])
  (emit-boolean [em b as-map-key cache])
  (emit-integer [em i as-map-key cache])
  (emit-double [em d as-map-key cache])
  (emit-binary [em b as-map-key cache])
  (array-size [em a])
  (emit-array-start [em size])
  (emit-array-end [em])
  (map-size [em m])
  (emit-map-start [em size])
  (emit-map-end [em])
  (emit-quoted [em o cache])
  (flush-writer [em stm])
  (prefers-strings [em]))

(def JSON_INT_MAX (Math/pow 2 53))
(def JSON_INT_MIN (- 0 JSON_INT_MAX))

(extend-protocol Emitter
  JsonGenerator
  (emit-nil [^JsonGenerator jg as-map-key cache]
    (if as-map-key
      (emit-string jg ESC "_" nil as-map-key cache)
      (.writeNull jg)))

  ;; could write string parts w/o combining them, but have to manage escaping,
  ;; see: https://github.com/FasterXML/jackson-docs/wiki/JacksonSampleQuoteChars
  (emit-string [^JsonGenerator jg prefix tag s as-map-key cache]
    (let [s ^String (cache-write cache (str prefix tag s) as-map-key)]
      (if as-map-key
        (.writeFieldName jg s)
        (.writeString jg s))))

  (emit-boolean [^JsonGenerator jg b as-map-key cache]
    (if as-map-key
      (emit-string jg ESC "?" (.substring (str b) 0 1) as-map-key cache)
      (.writeBoolean jg b)))

  (emit-integer [^JsonGenerator jg i as-map-key cache]
    (if (or as-map-key (string? i) (> i JSON_INT_MAX) (< i JSON_INT_MIN))
      (emit-string jg ESC "i" i as-map-key cache)
      (.writeNumber jg ^long i)))

  (emit-double [^JsonGenerator jg d as-map-key cache]
    (if as-map-key
      (emit-string jg ESC "d" d as-map-key cache)
      (.writeNumber jg ^double d)))

  (emit-binary [^JsonGenerator jg b as-map-key cache]
    (emit-string jg ESC "b" (Base64/encodeBase64String b) as-map-key cache))

  (array-size [^JsonGenerator jg _] nil)
  (emit-array-start [^JsonGenerator jg _] (.writeStartArray jg))
  (emit-array-end [^JsonGenerator jg] (.writeEndArray jg))
  (map-size [^JsonGenerator jg _] nil)
  (emit-map-start [^JsonGenerator jg _] (.writeStartObject jg))
  (emit-map-end [^JsonGenerator jg] (.writeEndObject jg))
  (emit-quoted [^JsonGenerator jg o cache]
    (emit-map-start jg 1)
    (emit-string jg ESC_TAG "'" nil true cache)
    (marshal jg o false cache)
    (emit-map-end jg))
  (flush-writer [^JsonGenerator jg _] (.flush jg))
  (prefers-strings [_] true))

(def MSGPACK_INT_MAX (bigint (Math/pow 2 63)))
(def MSGPACK_INT_MIN (- 0 MSGPACK_INT_MAX))

(extend-protocol Emitter
  Packer
  (emit-nil [^Packer p as-map-key cache] (.writeNil p))

  (emit-string [^Packer p prefix tag s as-map-key cache]
    (let [s ^String (cache-write cache (str prefix tag s) as-map-key)]
      (.write p s)))

  (emit-boolean [^Packer p b as-map-key cache] (.write p b))

  (emit-integer [^Packer p i as-map-key cache]
    (if (or (string? i) (> i MSGPACK_INT_MAX) (< i MSGPACK_INT_MIN))
      (emit-string p ESC "i" i as-map-key cache)
      (if (instance? clojure.lang.BigInt i)
        (.write p ^BigInteger (biginteger i))
        (if (instance? BigInteger i)
          (.write p ^BigInteger i)
          (.write p (long i))))))

  (emit-double [^Packer p d as-map-key cache] (.write p ^double d))

  (emit-binary [^Packer p b as-map-key cache]
    (emit-string p ESC "b" (Base64/encodeBase64String b) as-map-key cache))

  (array-size [^Packer p iter] (count iter))
  (emit-array-start [^Packer p size] (.writeArrayBegin p size))
  (emit-array-end [^Packer p] (.writeArrayEnd p))
  (map-size [^Packer p iter] (count iter))
  (emit-map-start [^Packer p size] (.writeMapBegin p size))
  (emit-map-end [^Packer p] (.writeMapEnd p))
  (emit-quoted [^Packer p o cache]
    (marshal p o false cache))
  (flush-writer [^Packer p ^OutputStream s]
    (.flush p)
    (flush-stream s))
  (prefers-strings [_] false))

(defn emit-ints
  [em ^ints src cache]
  (areduce src i res nil (emit-integer em (aget src i) false cache)))

(defn emit-shorts
  [em ^shorts src cache]
  (areduce src i res nil (emit-integer em (aget src i) false cache)))

(defn emit-longs
  [em ^longs src cache]
  (areduce src i res nil (emit-integer em (aget src i) false cache)))

(defn emit-floats
  [em ^floats src cache]
  (areduce src i res nil (emit-double em (aget src i) false cache)))

(defn emit-doubles
  [em ^doubles src cache]
  (areduce src i res nil (emit-double em (aget src i) false cache)))

(defn emit-chars
  [em ^chars src cache]
  (areduce src i res nil (marshal em (aget src i) false cache)))

(defn emit-booleans
  [em ^booleans src cache]
  (areduce src i res nil (emit-boolean em (aget src i) false cache)))

(defn emit-objs
  [em ^objects src cache]
  (areduce src i res nil (marshal em (aget src i) false cache)))

(defn emit-array
  [em iterable _ cache]
  (emit-array-start em (array-size em iterable))
  (if-not (.isArray ^Class (type iterable))
    (reduce (fn [_ i] (marshal em i false cache)) nil iterable)
    (condp = (type iterable)
      (type (short-array 0)) (emit-shorts em iterable cache)
      (type (int-array 0)) (emit-ints em iterable cache)
      (type (long-array 0)) (emit-longs em iterable cache)
      (type (float-array 0)) (emit-floats em iterable cache)
      (type (double-array 0)) (emit-doubles em iterable cache)
      (type (char-array 0)) (emit-chars em iterable cache)
      (type (boolean-array 0)) (emit-booleans em iterable cache)
      (emit-objs em iterable cache)))
  (emit-array-end em))

(defn emit-map
  [em iterable _ cache]
  (emit-map-start em (map-size em iterable))
  (reduce (fn [_ [k v]]
            (marshal em k true cache)
            (marshal em v false cache))
          nil iterable)
  (emit-map-end em))

(defprotocol Handler
  (tag [_ h])
  (rep [h o])
  (str-rep [h o]))

(declare handler)

(deftype AsTag [tag rep str])

(defn as-tag [tag rep str] (AsTag. tag rep str))

(deftype Quote [o])

(defn quoted [o] (Quote. o))

(deftype TaggedValue [tag rep]
  java.lang.Object
  (equals [t o]
    (and (= (type o) (type t))
         (= (.tag t) (.tag ^TaggedValue o))
         (= (.rep t) (.rep ^TaggedValue o))))
  (hashCode [t]
    (reduce unchecked-multiply-int
            17
            [31 (.hashCode (.tag t)) 31 (.hashCode (.rep t))])))

(defn tagged-value [tag rep] (TaggedValue. tag rep))

(defn emit-tagged-map
  [em tag rep _ cache]
  (emit-map-start em 1)
  (emit-string em ESC_TAG tag nil true cache)
  (marshal em rep false cache)
  (emit-map-end em))

(defn emit-encoded
  [em h tag o as-map-key cache]
  ;;(prn "EMIT ENCODED" em h tag o as-map-key cache)
  (if (= (.length ^String tag) 1)
    (let [rep (rep h o)]
      (if (string? rep)
        (emit-string em ESC tag rep as-map-key cache)
        (if (or as-map-key (prefers-strings em))
          (let [rep (str-rep h o)]
            (if (string? rep)
              (emit-string em ESC tag rep as-map-key cache)
              (throw (ex-info "Cannot be encoded as string" {:tag tag :rep rep :o o}))))
          (emit-tagged-map em tag rep as-map-key cache))))
    (if as-map-key
      (throw (ex-info "Cannot be used as map key" {:tag tag :rep rep :o o}))
      (emit-tagged-map em tag (rep h o) as-map-key cache))))

(defn marshal
  [em o as-map-key cache]
  ;;(prn "marshal" o (tag o) (rep o))
  (let [h (handler o)
        tag (when h (tag h o))
        rep (when h (rep h o))]
    (if (and h tag)
      ;;(prn "marshal" tag rep)
      (case tag
        "_" (emit-nil em as-map-key cache)
        "s" (emit-string em nil nil (escape rep) as-map-key cache)
        "?" (emit-boolean em (boolean rep) as-map-key cache)
        "i" (emit-integer em rep as-map-key cache)
        "d" (emit-double em rep as-map-key cache)
        "b" (emit-binary em rep as-map-key cache)
        "'" (emit-quoted em rep cache)
        "array" (emit-array em rep as-map-key cache)
        "map" (emit-map em rep as-map-key cache)
        (emit-encoded em h tag o as-map-key cache))
      (throw (ex-info "Not supported" {:o o :type (type o)})))))

(defn maybe-quoted
  [o]
  (if-let [h (handler o)]
    (if (= (.length ^String (tag h o)) 1)
      (quoted o)
      o)
    (throw (ex-info "Not supported" {:o o :type (type o)})))  )

(defn marshal-top
  [em o cache]
  (marshal em
           (maybe-quoted o)
           false
           cache))

(def ^:private thread-local-utc-date-format
  ;; SimpleDateFormat is not thread-safe, so we use a ThreadLocal proxy for access.
  ;; http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4228335
  (proxy [ThreadLocal] []
    (initialValue []
      (doto (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        (.setTimeZone (java.util.TimeZone/getTimeZone "GMT"))))))

(defn stringable-keys?
  [m]
  (let [ks (keys m)]
    (every? #(= (.length ^String (tag (handler %) %)) 1) ks)))

(def default-handlers
{
 (type (byte-array 0))
 (reify Handler
   (tag [_ _] "b")
   (rep [_ bs] bs)
   (str-rep [_ _] nil))

 nil
 (reify Handler
   (tag [_ _] "_")
   (rep [_ _] nil)
   (str-rep [_ _] nil))

 java.lang.String
 (reify Handler
   (tag [_ _] "s")
   (rep [_ s] s)
   (str-rep [_ s] s))

 java.lang.Boolean
 (reify Handler
   (tag [_ _] "?")
   (rep [_ b] b)
   (str-rep [_ b] (str b)))

 java.lang.Byte
 (reify Handler
   (tag [_ _] "i")
   (rep [_ b] b)
   (str-rep [_ b] (str b)))

 java.lang.Short
 (reify Handler
   (tag [_ _] "i")
   (rep [_ s] s)
   (str-rep [_ s] (str s)))

 java.lang.Integer
 (reify Handler
   (tag [_ _] "i")
   (rep [_ i] i)
   (str-rep [_ i] (str i)))

 java.lang.Long
 (reify Handler
   (tag [_ _] "i")
   (rep [_ l] l)
   (str-rep [_ l] (str l)))

 java.math.BigInteger
 (reify Handler
   (tag [_ _] "i")
   (rep [_ bi] bi)
   (str-rep [_ bi] (str bi)))

 clojure.lang.BigInt
 (reify Handler
   (tag [_ _] "i")
   (rep [_ bi] bi)
   (str-rep [_ bi] (str bi)))

 java.lang.Float
 (reify Handler
   (tag [_ _] "d")
   (rep [_ f] f)
   (str-rep [_ f] (str f)))

 java.lang.Double
 (reify Handler
   (tag [_ _] "d")
   (rep [_ d] d)
   (str-rep [_ d] (str d)))

 java.util.Map
 (reify Handler
   (tag [_ m] (if (stringable-keys? m) "map" "cmap"))
   (rep [_ m] (if (stringable-keys? m)
              (.entrySet ^java.util.Map m)
              (as-tag "array" (mapcat identity (.entrySet ^java.util.Map m)) nil)))
   (str-rep [_ _] nil))

 java.util.List
 (reify Handler
   (tag [_ l] (if (seq? l) "list" "array"))
   (rep [_ l] (if (seq? l) (as-tag "array" l nil) l))
   (str-rep [_ _] nil))

 AsTag
 (reify Handler
   (tag [_ e] (.tag ^AsTag e))
   (rep [_ e] (.rep ^AsTag e))
   (str-rep [_ e] (.str ^AsTag e)))

 Quote
 (reify Handler
   (tag [_ q] "'")
   (rep [_ q] (.o ^Quote q))
   (str-rep [_ q] nil))

 TaggedValue
 (reify Handler
   (tag [_ tv] (.tag ^TaggedValue tv))
   (rep [_ tv] (.rep ^TaggedValue tv))
   (str-rep [_ _] nil))

 java.lang.Object
 (reify Handler
   (tag [_ o] (when (.isArray ^Class (type o)) "array"))
   (rep [_ o] (when (.isArray ^Class (type o)) o)))

 ;; extensions

 clojure.lang.Keyword
 (reify Handler
   (tag [_ _] ":")
   (rep [_ kw] (nsed-name kw))
   (str-rep [_ kw] (rep _ kw)))

 clojure.lang.Ratio
 (reify Handler
   (tag [_ _] "ratio")
   (rep [_ r] (as-tag "array" [(numerator r) (denominator r)] nil))
   (str-rep [_ _] nil))

 java.math.BigDecimal
 (reify Handler
   (tag [_ _] "f")
   (rep [_ bigdec] (str bigdec))
   (str-rep [_ bigdec] (rep _ bigdec)))

 java.util.Date
 (reify Handler
   (tag [_ _] "t")
   (rep [_ inst] (.getTime ^java.util.Date inst))
   (str-rep [_ inst]
     (let [format (.get ^ThreadLocal thread-local-utc-date-format)]
       (.format ^java.text.SimpleDateFormat format inst))))

 java.util.UUID
 (reify Handler
   (tag [_ _] "u")
   (rep [_ uuid]
     [(.getMostSignificantBits ^java.util.UUID uuid)
      (.getLeastSignificantBits ^java.util.UUID uuid)])
   (str-rep [_ uuid] (str uuid)))

 java.net.URI
 (reify Handler
   (tag [_ _] "r")
   (rep [_ uri] (str uri))
   (str-rep [_ uri] (rep _ uri)))

 clojure.lang.Symbol
 (reify Handler
   (tag [_ _] "$")
   (rep [_ sym] (nsed-name sym))
   (str-rep [_ sym] (rep _ sym)))

 java.lang.Character
 (reify Handler
   (tag [_ _] "c")
   (rep [_ c] (str c))
   (str-rep [_ c] (rep _ c)))

 java.util.Set
 (reify Handler
   (tag [_ _] "set")
   (rep [_ s] (as-tag "array" s nil))
   (str-rep [_ s] nil))
 })

(def ^:private ^:dynamic *handlers* default-handlers)

(defn get-itf-handler [^Class t]
  (let [possible (loop [t t possible {}]
                   (if (not= t Object)
                     (recur (.getSuperclass t)
                            (merge possible
                                   (reduce (fn [m itf]
                                             (if-let [h (get *handlers* itf)]
                                               (assoc m itf h)
                                               m))
                                           {}
                                           (.getInterfaces t))))
                     possible))]
    (condp = (.size ^java.util.Map possible)
      0 nil
      1 (let [entries (.entrySet ^java.util.Map possible)
              entry (-> entries .iterator .next)]
          (.getValue ^java.util.Map$Entry entry))
      (throw (ex-info (str "More than one match for type " t)
                      {:type t})))))


(defn get-base-handler [^Class t]
  (loop [t (.getSuperclass t)]
    (when (not= t Object)
      (if-let [h (get *handlers* t)]
        h
        (recur (.getSuperclass t))))))

(defn handler [o]
  ;; look for handler in handlers
  (let [t (type o)]
    (if-let [h (get *handlers* t)]
      h
      (when-let [h (or (get-base-handler t)
                       (get-itf-handler t)
                       (get *handlers* Object))]
        (set! *handlers* (assoc *handlers* t h))
        h))))

(deftype Writer [marshaler stm opts])

(defprotocol Writerable
  (make-writer [_ type opts]))

(extend-protocol Writerable
  OutputStream
  (make-writer [^OutputStream stm type opts]
    (Writer.
     (case type
       :json (.createGenerator (JsonFactory.) stm)
       :msgpack (.createPacker (MessagePack.) stm))
     stm
     opts))
  java.io.Writer
  (make-writer [^java.io.Writer w type opts]
    (Writer.
     (case type
       :json (.createGenerator (JsonFactory.) w)
       :msgpack (throw (ex-info "Cannot create :msgpack writer on top of java.io.Writer, must use java.io.OutputStream" {})))
     nil
     opts)))

(extend-protocol Flushable
  OutputStream
  (flush-stream [stm]
    (.flush stm)))

(defn writer
  ([o type] (writer o type {}))
  ([o type {:keys [handlers] :as opts}]
     (if (#{:json :msgpack} type)
       (make-writer o type opts)
       (throw (ex-info "Type must be :json or :msgpack" {:type type})))))

(defn write [^Writer writer o]
  (let [m (.marshaler writer)
        {:keys [handlers]} (.opts writer)]
    (binding [*handlers* (merge default-handlers handlers)]
      (marshal-top m o (write-cache)))
    ;; can we configure JsonGenerator to automatically flush writes?
    (flush-writer m (.stm writer))))

(defn make-handler
  [tag-fn rep-fn str-rep-fn]
  (reify Handler
    (tag [_ o] (tag-fn o))
    (rep [_ o] (rep-fn o))
    (str-rep [_ o] (str-rep-fn o))))

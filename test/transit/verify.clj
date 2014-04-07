(ns transit.verify
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io])
  (:import [java.io PrintStream ByteArrayOutputStream ByteArrayInputStream]))

;; TODO: dynamically find `bin/roundtrip` commands
(def command "../transit-java/bin/roundtrip")

(defn write-transit [o]
  (let [out (ByteArrayOutputStream.)
        w (w/writer out :json)]
    (w/write w o)
    (.toString out)))

(defn read-transit [t]
  (let [in (ByteArrayInputStream. (.getBytes t))
        r (r/reader in :json)]
    (r/read r)))

(defn start-process [command]
  (let [p (.start (ProcessBuilder. [command]))
        out (PrintStream. (.getOutputStream p))
        in (.getInputStream p)]
    {:out out :in in :p p}))

(defn stop-process [proc]
  (.write (:out proc) 3) ;; send Ctrl+C
  (.flush (:out proc))
  (.destroy (:p proc)))

(defn write-to-stream [proc transit-data]
  (.print (:out proc) transit-data)
  (.flush (:out proc)))

(defn continue? [prev c counter open close]
  (cond (and (not= prev \^) (= c open)) inc
        (and (not= prev \^) (= c close)) (when (pos? (dec counter)) dec)
        :else identity))

(defn read-from-stream [proc]
  (loop [state :wait
         counter 0
         prev nil
         s nil]
    (let [c (.read (:in proc))]
    (if (== c -1)
      s
      (let [c (char c)]
        (case state
          :wait (cond (= c \[) (recur :array (inc counter) c (str s c))
                      (= c \{) (recur :object (inc counter) c (str s c))
                      :else (recur state counter c s))
          :array (if-let [f (continue? prev c counter \[ \])]
                   (recur state (f counter) c (str s c))
                   (str s c))
          :object (if-let [f (continue? prev c counter \{ \})]
                    (recur state (f counter) c (str s c))
                    (str s c))))))))

(defn roundtrip-transit-string [proc transit-out compare-transit?]
  (write-to-stream proc transit-out)
  ;; TODO: need a timeout here so this doesn't block forever
  (let [transit-in (read-from-stream proc)
        data-out (read-transit transit-out)
        data-in (read-transit transit-in)]
    {:transit-expected transit-out
     :transit-actual transit-in
     :data-expected data-out
     :data-actual data-in
     ;; only checks the top level type
     :status (if (and (= (type data-out) (type data-in))
                      (= data-out data-in)
                      (or (not compare-transit?) (= (count transit-out) (count transit-in))))
               :success
               :error)}))

(defn roundtrip-edn [proc edn compare-transit?]
  (let [transit-out (write-transit edn)
        result (roundtrip-transit-string proc transit-out compare-transit?)]
    (if (not= edn (:data-actual result) (:data-expected result))
      (assoc result :status :warning)
      result)))

(defn test-sample-files
  ([] (test-sample-files false))
  ([compare-transit?]
     (let [files (filter #(and (.isFile %) (.endsWith (.getName %) ".json"))
                         (file-seq (io/file "../transit/simple-examples")))
           proc (start-process command)]
       (println "Testing against" (count files) "sample files.")
       (doseq [file files]
         (let [result (roundtrip-transit-string proc (slurp file) compare-transit?)]
           (when (contains? #{:warning :error} (:status result))
             (do (println (.getName file) (:status result))
                 (println "Expected:" (:transit-expected result))
                 (println "Actual:  " (:transit-actual result))))))
       (stop-process proc))))

(defn test-edn
  ([] (test-edn false))
  ([compare-transit?]
     ;; TODO: use generators
     (let [forms [true false :a :foo :foobar 42 'foo (java.util.Date.) 1/3 \t "hello" "~hello"
                  [1 2 3] `(7 23 5) {:foo :bar} #{:a :b :c}]
           proc (start-process command)]
       (println "Testing against" (count forms) "forms.")
       (doseq [form forms]
         (let [result (roundtrip-edn proc form compare-transit?)]
           (when (= :warning (:status result))
             (do (println (:status result))
                 (println "Expected:" (pr-str form))
                 (println "Actual:  " (pr-str (:data-actual result)))))
           (when (= :error (:status result))
             (do (println (:status result))
                 (println (pr-str form))
                 (println "Expected:" (:transit-expected result))
                 (println "Actual:  " (:transit-actual result))))))
       (stop-process proc))))

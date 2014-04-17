;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.verify
  "Provides tools for testing all transit implementations at
  once. Tests each implementaion with several sources of data:
  exemplar files, problem edn data, problem transit data and generated
  data. In addition, it can capture comparative timing results.

  From the REPL, the main entry point is `verify-all` which takes an
  options map as an argument. With an empty map it will test each
  encoding for all implementations located in sibling project
  directories which have a `bin/roundtrip` script. Options can be used
  to control which project is tested, which encoding to test, turn on
  generative testing and collect timing information."
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io]
            [transit.generators :as gen]
            [transit.corner-cases :as cc])
  (:import [java.io PrintStream ByteArrayOutputStream ByteArrayInputStream
            BufferedInputStream BufferedOutputStream FileInputStream]))

(def TIMEOUT
  "Timeout for roundtrip requests to native implementation"
  10000)

(defn read-bytes
  "Read the contents of the passed file into a byte array and return
  the byte array."
  [file]
  (assert (.exists file) "file must exist")
  (assert (.isFile file) "file must actually be a file")
  (let [size (.length file)
        bytes (make-array Byte/TYPE size)
        in (BufferedInputStream. (FileInputStream. file))]
    (loop [n (.read in bytes 0 size)
           total 0]
      (if (or (= n -1) (= (+ n total) size))
        bytes
        (let [offset (+ n total)
              size (- size offset)]
          (recur (.read in bytes offset size) offset))))))

(defn write-transit
  "Given an object and an encoding, return a byte array containing the
  encoded value of the object."
  [o encoding]
  (let [out (ByteArrayOutputStream.)
        w (w/writer out encoding)]
    (w/write w o)
    (.toByteArray out)))

(defn read-transit
  "Given a byte array containing an encoded value and the encoding used,
  return the decoded object."
  [bytes encoding]
  (try
    (let [in (ByteArrayInputStream. bytes)
          r (r/reader in encoding)]
      (r/read r))
    (catch Throwable e
      ::read-error)))

(defn start-process [command encoding]
  ;; TODO: what if there is exception here? need to report that we
  ;; couldn't start the process?
  ;; TODO: how do we communicate that the encoding is not supported?
  (let [p (.start (ProcessBuilder. [command (name encoding)]))
        out (BufferedOutputStream. (.getOutputStream p))
        in (BufferedInputStream. (.getInputStream p))]
    ;; r/reader does not return until data starts to flow over in
    {:out out :p p :reader (future (r/reader in encoding))}))

(defn stop-process [proc]
  (try
    (.write (:out proc) 3) ;; send Ctrl+C
    (.flush (:out proc))
    (.destroy (:p proc))
    (catch Throwable e
      (println "WARNING! Exception while stopping process.")
      (println (.toString e)))))

;; TODO: failure to write means that the process has died
;; catch and throw the way we do with read-response
(defn write-to-stream [proc transit-data]
  (.write (:out proc) transit-data 0 (count transit-data))
  (.flush (:out proc)))

(defn read-response [proc transit-out timeout-ms]
  (let [f (future (r/read @(:reader proc)))]
    (let [response (deref f timeout-ms ::timeout)]
      (if (= response ::timeout)
        (throw (ex-info "Response timeout" {:status :timeout :p proc :transit transit-out}))
        response))))

(defn roundtrip-transit [proc transit-out encoding]
  (write-to-stream proc transit-out)
  (let [data-in (read-response proc transit-out TIMEOUT)
        data-out (read-transit transit-out encoding)]
    {:transit-expected (String. transit-out)
     :data-expected data-out
     :data-actual data-in
     ;; only checks the top level type
     :status (if (and (= (type data-out) (type data-in))
                      (= data-out data-in))
               :success
               :error)}))

(defn roundtrip-edn [proc edn encoding]
  (try
    (let [transit-out (write-transit edn encoding)]
      (roundtrip-transit proc transit-out encoding))
    (catch Throwable e
      (if (= (-> e ex-data :status) :timeout)
        (throw (ex-info "Response timeout" (assoc (ex-data e) :edn edn)))
        (throw e)))))

(defn test-transit [transits proc encoding]
  (mapv #(roundtrip-transit proc % encoding) transits))

(defn test-edn [forms proc encoding]
  (mapv #(roundtrip-edn proc % encoding) forms))

(defn test-timing [transits proc encoding]
  (println "collecting timing information...")
  (dotimes [x 100]
    (mapv #(roundtrip-transit proc % encoding) transits))
  (let [start (System/currentTimeMillis)]
    (mapv #(roundtrip-transit proc % encoding) transits)
    (- (System/currentTimeMillis) start)))

(def extension {:json ".json"
                :msgpack ".mp"})

(defn exemplar-transit [encoding]
  (map #(read-bytes %)
       (filter #(and (.isFile %) (.endsWith (.getName %) (extension encoding)))
               (file-seq (io/file "../transit/simple-examples")))))

(defn filter-tests [proc encoding opts]
  (let [transit-exemplars (exemplar-transit encoding)]
    (filter #((:pred %) proc encoding opts)
            [{:pred (constantly true)
              :path [:tests :exemplar-file]
              :test #(test-transit transit-exemplars proc encoding)}
             {:pred (constantly true)
              :path [:tests :corner-case-edn]
              :test #(test-edn cc/forms proc encoding)}
             {:pred (fn [_ e _] (= e :json))
              :path [:tests :corner-case-transit-json]
              :test #(test-transit (map (fn [s] (.getBytes s)) cc/transit-json) proc encoding)}
             {:pred (fn [_ _ o] (:gen o))
              :path [:tests :generated-edn]
              :test #(test-edn (:generated-forms opts) proc encoding)}
             {:pred (fn [_ _ o] (:time o))
              :path [:time]
              :test #(let [ms (test-timing transit-exemplars proc encoding)]
                       {:ms ms
                        :count (count transit-exemplars)
                        :encoding encoding})}])))

(defn verify-impl-encoding [command encoding opts]
  (assert (contains? extension encoding)
          (str "encoding must be on of" (keys extension)))
  (let [proc (start-process command encoding)
        results {:command command
                 :encoding encoding}]
    (try
      (let [tests (filter-tests proc encoding opts)
            results (reduce (fn [r {:keys [path test]}]
                              (assoc-in r path (test)))
                            results
                            tests)]
        (stop-process proc)
        results)
      (catch Throwable e
        (stop-process proc)
        (merge results {:exception e})))))

(declare report)

(defn- run-test [project encoding opts]
  (println "testing" project "...")
  (let [command (str "../" project "/bin/roundtrip")]
    (if (= encoding :msgpack)
      (println "msgpack tests are disabled until we have a working implementation.")
      (report (-> (verify-impl-encoding command encoding opts)
                  (assoc :project project))
              opts))))

(defn verify-impl [project {:keys [enc] :as opts}]
  (doseq [e (if enc [enc] [:json :msgpack])]
    (run-test project e opts)))

(defn verify-all [{:keys [impls] :as opts}]
  (let [root (io/file "../")
        testable-impls (keep #(let [script (io/file root (str % "/bin/roundtrip"))]
                                (when (.exists script) %))
                             (.list root))
        forms (when-let [n (:gen opts)]
                (take n (repeatedly gen/ednable)))]
    (doseq [impl testable-impls]
      (when (or (not impls)
                (contains? impls impl))
        (verify-impl impl (assoc opts :generated-forms forms))))))

(defn read-options [args]
  (reduce (fn [a [[k] v]]
            (case k
              "-impls" (assoc a :impls (set (mapv #(str "transit-" %) v)))
              "-enc" (assoc a :enc (keyword (first v)))
              "-gen" (assoc a :gen (Integer/valueOf (first v)))
              "-time" (assoc a :time true)
              a))
          {}
          (partition-all 2 (partition-by #(.startsWith % "-") args))))

(def ^:dynamic *style* false)

(defn -main [& args]
  (binding [*style* true]
    (verify-all (read-options args))
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reporting

(def styles {:reset "[0m"
             :red "[31m"
             :green "[32m"
             :bright "[1m"})

(defn with-style [style & strs]
  (let [s (apply str (interpose " " strs))]
    (if (and *style* (not= style :none))
      (str \u001b (style styles) s \u001b (:reset styles))
      s)))

(defn timeout-result? [results]
  (when-let [e (:exception results)]
    (= (:status (ex-data e)) :timeout)))

(defn exception-result? [results]
  (:exception results))

(defn not-implemented? [results]
  (every? #(= (:data-actual %) ::read-error) (apply concat (vals (:tests results)))))

;; TODO: when there is an exception, print out the form that was being
;; transmitted
;; TODO: would be nice if we could do the same thing we do with
;; timeouts and show the previous thing as well
(defn report [{:keys [project command encoding tests time] :as results} opts]
  (println (with-style :bright "Project: " project))
  (println "Command: " command)
  (println "Encoding:" encoding)
  (when time
    (println (with-style :bright
               (format "Time: %s ms to roundtrip %s %s exemplar files"
                       (:ms time)
                       (:count time)
                       (name (:encoding time))))))
  (cond (timeout-result? results)
        (let [timeout-form (-> results :exception ex-data :edn)
              [prev curr] (first (filter #(= (last %) timeout-form)
                                         (partition 2 1 (:generated-forms opts))))]
          (println (with-style :red "Response Timeout"))
          (println (with-style :red "Timeout while processing:"))
          (println (pr-str timeout-form))
          (println (with-style :red "Previous form was:"))
          (println (pr-str prev)))
        (exception-result? results)
        (do (println results)
            (.printStackTrace (:exception results)))
        (not-implemented? results)
        (println (with-style :red "Not Implemented"))
        :else
        ;; TODO: need to do something better for printing transit data
        ;; (when bytes)
        (doseq [[k v] tests]
          (let [errors (filter #(= (:status %) :error) v)
                ecnt (count errors)
                pcnt (- (count v) ecnt)
                style (if (pos? ecnt) :red :green)]
            (println (with-style style (format "Testing with %s %s inputs"
                                               (count v) k)))
            (println (with-style style (format "Summary: passed: %s, errors: %s"
                                               pcnt ecnt)))
            (when (pos? ecnt)
              (do (doseq [error errors]
                    (println "Sent Transit:" (pr-str (:transit-expected error)))
                    (println "Expected:    " (pr-str (:data-expected error)))
                    (println "Actual:      " (pr-str (:data-actual error))))))))))

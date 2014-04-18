;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.verify
  "Provides tools for testing all transit implementations at
  once. Tests each implementaion with several sources of data:
  exemplar files, problem EDN data, problem transit data and generated
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
            BufferedInputStream BufferedOutputStream FileInputStream]
           [org.apache.commons.codec.binary Hex]))

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

(defn start-process
  "Given a command and enconding, start a process which can roundtrip
  transit data. The command is the path to any program which will
  start a roundtrip process. The enconding must be either `:json` or
  `:msgpack`. Returns a map (process map) with keys `:out` the output
  stream which can be used to send data to the process, `:p` the
  process and `:reader` a transit reader."
  [command encoding]
  ;; TODO: what if there is exception here? need to report that we
  ;; couldn't start the process?
  ;; TODO: how do we communicate that the encoding is not supported?
  (let [p (.start (ProcessBuilder. [command (name encoding)]))
        out (BufferedOutputStream. (.getOutputStream p))
        in (BufferedInputStream. (.getInputStream p))]
    ;; r/reader does not return until data starts to flow over in
    {:out out :p p :reader (future (r/reader in encoding))}))

(defn stop-process
  "Given a process, stop the process started by
  `start-process`. Sends Ctrl-C (SIGINT) to the process before
  attempting to destroy the process."
  [proc]
  (try
    (.write (:out proc) 3) ;; send Ctrl+C
    (.flush (:out proc))
    (.destroy (:p proc))
    (catch Throwable e
      (println "WARNING! Exception while stopping process.")
      (println (.toString e)))))

(defn write-to-stream
  "Given a process and a byte array of transit data, write this
  data to the output stream. Throws an exception of the output stream
  is closed."
  [proc transit-data]
  #_(println "Send bytes:")
  #_(println (count transit-data))
  #_(println (Hex/encodeHexString transit-data))
  (try
    (.write (:out proc) transit-data 0 (count transit-data))
    (.flush (:out proc))
    (catch Throwable _
      (throw (ex-info "Disconnected" {:status :disconnected :p proc :transit transit-data})))))

(defn read-response
  "Given a process and a timeout in milliseconds, attempt to read
  a transit value.  If the read times out, returns `::timeout`.`"
  [proc timeout-ms]
  (let [f (future (r/read @(:reader proc)))]
    (deref f timeout-ms ::timeout)))

(defn roundtrip-transit
  "Given a process, a byte array of transit data and an encoding,
  roundtrip the transit data and return a map of test results. Throws
  an exception if sending transit data causes a timeout. The results
  map contains the expected and actual Clojure data and the test
  status: one of `:error` or `:success`."
  [proc transit-out encoding]
  (write-to-stream proc transit-out)
  (let [data-in (read-response proc TIMEOUT)
        data-out (read-transit transit-out encoding)]
    (if (= data-in ::timeout)
      (throw (ex-info "Response timeout" {:status :timeout :p proc :transit transit-out}))
      {:transit-expected (String. transit-out)
       :data-expected data-out
       :data-actual data-in
       ;; only checks the top level type
       :status (if (and (= (type data-out) (type data-in))
                        (= data-out data-in))
                 :success
                 :error)})))

(defn roundtrip-edn
  "Given a process, an EDN form and an encoding, roundtrip the EDN
  data and return a map of test results. See `roundtrip-transit`."
  [proc edn encoding]
  (try
    (let [transit-out (write-transit edn encoding)]
      (roundtrip-transit proc transit-out encoding))
    (catch Throwable e
      (if (contains? #{:disconnected :timeout} (-> e ex-data :status) )
        (throw (ex-info (.getMessage e) (assoc (ex-data e) :edn edn)))
        (throw e)))))

(defn test-transit
  "Given a collection of transit byte arrays, a process and the
  transit enconding, roundtrip each transit message and return a
  sequence of results."
  [transits proc encoding]
  (mapv #(roundtrip-transit proc % encoding) transits))

(defn test-edn
  "Given a collection of EDN forms, a process and a transit
  enconding, roundtrip each form and return a sequence of results."
  [forms proc encoding]
  (mapv #(roundtrip-edn proc % encoding) forms))

(defn test-timing
  "Given a collection of transit byte arrays, a process and the
  transit enconding, record the time in milliseconds that it takes
  to roundtrip all of the transit messages."
  [transits proc encoding]
  (dotimes [x 200]
    (mapv #(roundtrip-transit proc % encoding) transits))
  (let [start (System/nanoTime)]
    (mapv #(roundtrip-transit proc % encoding) transits)
    (quot (- (System/nanoTime) start) 1000000)))

(def extension {:json ".json"
                :msgpack ".mp"})

(defn exemplar-transit
  "Given an encoding, return a collection of transit byte arrays in
  that encoding. The byte arrays are loaded from the exemplar files in
  the `transit` repository."
  [encoding]
  (map #(read-bytes %)
       (filter #(and (.isFile %) (.endsWith (.getName %) (extension encoding)))
               (file-seq (io/file "../transit/simple-examples")))))

(defn filter-tests
  "Given a process, an encoding and options provided by the user,
  return a sequcnes of tests to run. Each test is represented as a map
  with `:path` and `:test` keys. The value at `:path` is the path into
  the results where the test results are to be stored. The value at
  `:test` is a no argument function which will run a single test."
  [proc encoding opts]
  (let [transit-exemplars (exemplar-transit encoding)]
    (filter #((:pred %) proc encoding opts)
            [{:pred (constantly true)
              :desc "exemplar file"
              :path [:tests :exemplar-file]
              :test #(test-transit transit-exemplars proc encoding)}
             {:pred (constantly true)
              :desc "EDN corner case"
              :path [:tests :corner-case-edn]
              :test #(test-edn cc/forms proc encoding)}
             {:pred (fn [_ e _] (= e :json))
              :desc "JSON transit corner case"
              :path [:tests :corner-case-transit-json]
              :test #(test-transit (map (fn [s] (.getBytes s)) cc/transit-json) proc encoding)}
             {:pred (fn [_ _ o] (:gen o))
              :desc "generated EDN"
              :path [:tests :generated-edn]
              :test #(test-edn (:generated-forms opts) proc encoding)}
             {:pred (fn [_ _ o] (:time o))
              :desc "timing"
              :path [:time]
              :test #(let [ms (test-timing transit-exemplars proc encoding)]
                       {:ms ms
                        :count (count transit-exemplars)
                        :encoding encoding})}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Running Tests

(defn verify-impl-encoding
  "Given a command string, an encoding and user provided options, run
  a collection of tests against a specific implementation of
  transit. The tests which are run are determined by the provided
  options and the encoding. The implementation which is tested is
  determined by the provided command string."
  [command encoding opts]
  (assert (contains? extension encoding)
          (str "encoding must be on of" (keys extension)))
  (let [proc (start-process command encoding)
        results {:command command
                 :encoding encoding}]
    (try
      (let [tests (filter-tests proc encoding opts)
            results (reduce (fn [r {:keys [path test desc]}]
                              (println (format "running \"%s\" test for %s encoding..."
                                               desc
                                               (name encoding)))
                              (assoc-in r path (test)))
                            results
                            tests)]
        (stop-process proc)
        results)
      (catch Throwable e
        (stop-process proc)
        (merge results {:exception e})))))

(declare report)

(defn- run-test
  "Given a project name, an encoding and user provided options, run
  tests against this project."
  [project encoding opts]
  (println (format "testing %s's %s encoding"
                   project
                   (name encoding)))
  (let [command (str "../" project "/bin/roundtrip")]
    (report (-> (verify-impl-encoding command encoding opts)
                (assoc :project project))
            opts)))

(defn verify-impl
  "Given a project name like 'transit-java', 'transit-clj' or
  'transit-ruby', and user provided options, run tests for each
  encoding specified in the options. Encoding can be either `:json` or
  `:msgpack`."
  [project {:keys [enc] :as opts}]
  (doseq [e (if enc [enc] [:json :msgpack])]
    (run-test project e opts)))

(defn verify-all
  "Given user provided options, run all tests specified by the
  options. If options is an empty map then run all tests against all
  implementations for both encodings."
  [{:keys [impls] :as opts}]
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

(defn read-options
  "Given a sequence of strings which are the command line arguments,
  return an options map."
  [args]
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

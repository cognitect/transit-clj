(ns transit.verify
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io])
  (:import [java.io PrintStream ByteArrayOutputStream ByteArrayInputStream
            BufferedInputStream BufferedOutputStream FileInputStream]
           [java.util Arrays]
           [org.apache.commons.codec.binary Hex]))

(def ^:dynamic *color* false)

(def colors {:reset "[0m"
             :red "[31m"
             :yellow "[33m"
             :green "[32m"})

(defn with-color [color & strs]
  (let [s (apply str (interpose " " strs))]
    (if (and *color* (not= color :none))
      (str \u001b (color colors) s \u001b (:reset colors))
      s)))

(defn read-bytes [file]
  (assert (.exists file) "file must exist")
  (assert (.isFile file) "file must actually be a file")
  (let [size (.length file)
        bytes (make-array Byte/TYPE size)
        in (BufferedInputStream. (FileInputStream. file))]
    (loop [g (.read in bytes 0 size)
           r 0]
      (if (or (= g -1) (= (+ g r) size))
        bytes
        (let [offset (+ g r)
              size (- size offset)]
          (recur (.read in bytes offset size) offset))))))

(defn write-transit [o encoding]
  (let [out (ByteArrayOutputStream.)
        w (w/writer out encoding)]
    (w/write w o)
    (.toByteArray out)))

(defn read-transit [bytes encoding]
  (try
    (let [in (ByteArrayInputStream. bytes)
          r (r/reader in encoding)]
      (r/read r))
    (catch Throwable e
      ::error)))

(defn start-process [command encoding]
  ;; TODO: what if there is execption here? need to report that we
  ;; couldn't start the process?
  ;; TODO: how do we communicate that the encoding is not supported?
  (let [p (.start (ProcessBuilder. [command (name encoding)]))
        out (BufferedOutputStream. (.getOutputStream p))
        in (BufferedInputStream. (.getInputStream p))]
    {:out out :in in :p p}))

(defn stop-process [proc]
  (try
    (.write (:out proc) 3) ;; send Ctrl+C
    (.flush (:out proc))
    (.destroy (:p proc))
    (catch Throwable e)))

(defn write-to-stream [proc transit-data]
  #_(println "Send bytes:")
  #_(println (Hex/encodeHexString transit-data))
  (.write (:out proc) transit-data 0 (count transit-data))
  (.flush (:out proc)))

(let [continue? (fn [prev c counter open close]
                  (cond (and (not= prev \^) (= c open)) inc
                        (and (not= prev \^) (= c close)) (when (pos? (dec counter)) dec)
                        :else identity))]
  (defn read-from-stream [proc]
    (let [bytes (ByteArrayOutputStream.)]
      (loop [state :wait
             counter 0
             prev nil]
        (let [b (.read (:in proc))]
          (if (== b -1)
            (.toByteArray bytes)
            (let [c (char b)]
              (case state
                :wait (cond (= c \[) (do (.write bytes b)
                                         (recur :array (inc counter) c))
                            (= c \{) (do (.write bytes b)
                                         (recur :object (inc counter) c))
                            :else (recur state counter c))
                :array (do (.write bytes b)
                           (if-let [f (continue? prev c counter \[ \])]
                             (recur state (f counter) c)
                             (.toByteArray bytes)))
                :object (do (.write bytes b)
                            (if-let [f (continue? prev c counter \{ \})]
                              (recur state (f counter) c)
                              (.toByteArray bytes)))))))))))

(defn read-response [proc timeout-ms]
  (let [f (future (read-from-stream proc))]
    (let [response (deref f timeout-ms ::timeout)]
      (if (= response ::timeout)
        (throw (ex-info "Response timeout" {:status :timeout :p proc}))
        response))))

(defn roundtrip-transit [proc transit-out encoding compare-transit?]
  (write-to-stream proc transit-out)
  (let [transit-in (read-response proc 10000)
        data-out (read-transit transit-out encoding)
        data-in (read-transit transit-in encoding)]
    #_(println "Response bytes:")
    #_(println (Hex/encodeHexString transit-in))
    ;; TODO: a better way to display transit data for msgpack
    {:transit-expected (String. transit-out)
     :transit-actual (String. transit-in)
     :data-expected data-out
     :data-actual data-in
     ;; only checks the top level type
     :status (if (and (= (type data-out) (type data-in))
                      (= data-out data-in)
                      (or (not compare-transit?) (= (count transit-out) (count transit-in))))
               :success
               :error)}))

(defn roundtrip-edn [proc edn encoding compare-transit?]
  (let [transit-out (write-transit edn encoding)
        result (roundtrip-transit proc transit-out encoding compare-transit?)]
    (if (not= edn (:data-actual result) (:data-expected result))
      (assoc result :status :warning :edn edn)
      result)))

(def extension {:json ".json"
                :msgpack ".mp"})

(defn test-sample-files
  ([proc encoding] (test-sample-files proc encoding false))
  ([proc encoding compare-transit?]
     (let [files (filter #(and (.isFile %) (.endsWith (.getName %) (extension encoding)))
                         (file-seq (io/file "../transit/simple-examples")))]
       ;; TODO: This is not working with msgpack
       ;; I don't think you can treat it as strings
       (mapv #(roundtrip-transit proc (read-bytes %) encoding compare-transit?)
             files))))

(defn test-edn
  ([proc encoding] (test-edn proc encoding false))
  ([proc encoding compare-transit?]
     ;; TODO: add generators
     ;; TODO: could still have some enumerated corner cases like this
     (let [forms [nil true false :a :foo 'f 'foo (java.util.Date.) 1/3 \t "f" "foo" "~foo"
                  [1 24 3] `(7 23 5) {:foo :bar} #{:a :b :c} 0 42
                  8987676543234565432178765987645654323456554331234566789]]
       (mapv #(roundtrip-edn proc % encoding compare-transit?)
             forms))))

(defn verify-impl-encoding [command encoding]
  (assert (contains? extension encoding)
          (str "encoding must be on of" (keys extension)))
  (let [proc (start-process command encoding)
        results {:command command
                 :encoding encoding}]
    (try
      (let [results (-> results
                        (assoc-in [:tests :sample-file] (test-sample-files proc encoding ))
                        (assoc-in [:tests :generated] (test-edn proc encoding)))]
        (stop-process proc)
        results)
      (catch Throwable e
        (stop-process proc)
        (merge results {:exception e})))))

(defn timeout? [results]
  (when-let [e (:exception results)]
    (= (:status (ex-data e)) :timeout)))

(defn exception? [results]
  (:exception results))

(defn not-implemented? [results]
  (every? #(= (:data-actual %) ::error) (apply concat (vals (:tests results)))))

(defn report [{:keys [project command encoding tests] :as results}]
  (println "Project: " project)
  (println "Command: " command)
  (println "Encoding:" encoding)
  (cond (timeout? results)
        (println (with-color :red "Response Timeout"))
        (exception? results)
        (do (println results)
            (.printStackTrace (:exception results)))
        (not-implemented? results)
        (println (with-color :red "Not Implemented"))
        :else
        ;; TODO: need to do something better for printing transit data
        ;; (when bytes)
        (doseq [[k v] tests]
          (let [warnings (filter #(= (:status %) :warning) v)
                errors (filter #(= (:status %) :error) v)
                wcnt (count warnings)
                ecnt (count errors)
                pcnt (- (count v) wcnt ecnt)
                color (cond (pos? ecnt) :red (pos? wcnt) :yellow :else :green)]
            (println (with-color color "Testing with" (count v) k "inputs"))
            (println (with-color color "Summary:"
                       "passed:" pcnt
                       ", errors:" ecnt
                       ", warnings:" wcnt))
            (cond (pos? wcnt)
                  (do (doseq [warn warnings]
                        (println "Expected:" (pr-str (:edn warn)))
                        (println "Actual:  " (pr-str (:data-actual warn)))
                        (println "Sent Transit:    " (pr-str (:transit-expected warn)))
                        (println "Received Transit:" (pr-str (:transit-actual warn)))))
                  (pos? ecnt)
                  (do (doseq [error errors]
                        (println "Expected:" (pr-str (:transit-expected error)))
                        (println "Actual:  " (pr-str (:transit-actual error))))))))))

(defn verify-impl [project]
  (let [command (str "../" project "/bin/roundtrip")]
    (report (-> (verify-impl-encoding command :json)
                (assoc :project project)))
    ;; TODO: :msgpack does not work against transit-clj which is the
    ;; only impl.
    #_(report (-> (verify-impl-encoding command :msgpack)
                (assoc :project project)))))

(defn verify-all []
  (let [root (io/file "../")
        testable-impls (keep #(let [script (io/file root (str % "/bin/roundtrip"))]
                                (when (.exists script) %))
                             (.list root))]
    (doseq [impl testable-impls]
      (verify-impl impl))))

(defn -main [& args]
  (binding [*color* true]
    ;; TODO: what arguments should be passed to verify-all?
    ;; -check-transit true (default is false)
    ;; -encoding json|msgpack (default is both)
    ;; -impl transit-java|transit-clj|etc (default to all)
    (verify-all)
    (shutdown-agents)))

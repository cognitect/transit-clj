(ns transit.verify
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io]
            [transit.generators :as gen]
            [transit.corner-cases :as cc])
  (:import [java.io PrintStream ByteArrayOutputStream ByteArrayInputStream
            BufferedInputStream BufferedOutputStream FileInputStream]
           [java.util Arrays]
           [org.apache.commons.codec.binary Hex]))

(def TIMEOUT 10000)

(def ^:dynamic *color* false)

(def colors {:reset "[0m"
             :red "[31m"
             :yellow "[33m"
             :green "[32m"
             :bright "[1m"})

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
      ::read-error)))

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
    (catch Throwable _
      ;; TODO: report this?
      )))

(defn write-to-stream [proc transit-data]
  #_(println "Send bytes:")
  #_(println (Hex/encodeHexString transit-data))
  (.write (:out proc) transit-data 0 (count transit-data))
  (.flush (:out proc)))

(defn read-from-stream [proc]
  (let [bytes (ByteArrayOutputStream.)]
    (loop [state `(:wait)
           counter 0
           prev nil]
      (let [b (.read (:in proc))]
        (if (== b -1)
          (.toByteArray bytes)
          (let [c (char b)]
            (case (first state)
              :wait (cond (= c \[) (do (.write bytes b)
                                       (recur '(:array) (inc counter) c))
                          (= c \{) (do (.write bytes b)
                                       (recur '(:object) (inc counter) c))
                          :else (recur state counter c))
              :string (do (.write bytes b)
                          (cond (and (= prev \\) (= c \\))
                                ;; set prev to non-escape char
                                (recur state counter \space)
                                (and (not= prev \\) (= c \"))
                                (recur (rest state) counter c)
                                :else (recur state counter c)))
              :array (do (.write bytes b)
                         (cond (and (not= prev \\) (= c \"))
                               (recur (conj state :string) counter c)
                               (and (= c \[))
                               (recur state (inc counter) c)
                               (and (= c \]) (pos? (dec counter)))
                               (recur state (dec counter) c)
                               (= c \])
                               (.toByteArray bytes)
                               :else (recur state counter c)))
              :object (do (.write bytes b)
                          (cond (and (not= prev \\) (= c \"))
                                (recur (conj state :string) counter c)
                                (and (= c \{))
                                (recur state (inc counter) c)
                                (and (= c \}) (pos? (dec counter)))
                                (recur state (dec counter) c)
                                (= c \})
                                (.toByteArray bytes)
                                :else (recur state counter c))))))))))

(defn read-response [proc transit-out timeout-ms]
  (let [f (future (read-from-stream proc))]
    (let [response (deref f timeout-ms ::timeout)]
      (if (= response ::timeout)
        (throw (ex-info "Response timeout" {:status :timeout :p proc :transit transit-out}))
        response))))

(defn roundtrip-transit [proc transit-out encoding]
  (write-to-stream proc transit-out)
  (let [transit-in (read-response proc transit-out TIMEOUT)
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
                      (= data-out data-in))
               :success
               :error)}))

(defn roundtrip-edn [proc edn encoding]
  (try
    (let [transit-out (write-transit edn encoding)
          result (roundtrip-transit proc transit-out encoding)]
      (if (not (or (= (:status result) :error)
                   (= edn (:data-actual result) (:data-expected result))))
        (assoc result :status :warning :edn edn)
        result))
    (catch Throwable e
      (if (= (-> e ex-data :status) :timeout)
        (throw (ex-info "Response timeout" (assoc (ex-data e) :edn edn)))
        (throw e)))))

(def extension {:json ".json"
                :msgpack ".mp"})

(defn test-exemplar-files [proc encoding]
  (let [files (filter #(and (.isFile %) (.endsWith (.getName %) (extension encoding)))
                      (file-seq (io/file "../transit/simple-examples")))]
    (mapv #(roundtrip-transit proc (read-bytes %) encoding)
          files)))

(defn test-corners-of-edn [proc encoding]
  (mapv #(roundtrip-edn proc % encoding) cc/forms))

(defn test-corners-of-transit-json [proc encoding]
  (mapv #(roundtrip-transit proc (.getBytes %) encoding) cc/transit-json))

(defn test-generated-edn [forms proc encoding]
  (mapv #(roundtrip-edn proc % encoding) forms))

(defn verify-impl-encoding [command encoding opts]
  (assert (contains? extension encoding)
          (str "encoding must be on of" (keys extension)))
  (let [proc (start-process command encoding)
        results {:command command
                 :encoding encoding}]
    (try
      (let [results (-> results
                        (assoc-in [:tests :exemplar-file] (test-exemplar-files proc encoding))
                        (assoc-in [:tests :corner-case-edn] (test-corners-of-edn proc encoding))
                        (assoc-in [:tests :corner-case-transit-json]
                                  (test-corners-of-transit-json proc encoding)))
            results (if (:gen opts)
                      (assoc-in results [:tests :generated-edn]
                                (test-generated-edn (:generated-forms opts) proc encoding))
                      results)]
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
  (every? #(= (:data-actual %) ::read-error) (apply concat (vals (:tests results)))))

(defn report [{:keys [project command encoding tests] :as results} opts]
  (println (with-color :bright "Project: " project))
  (println "Command: " command)
  (println "Encoding:" encoding)
  (cond (timeout? results)
        (let [timeout-form (-> results :exception ex-data :edn)
              [prev curr] (first (filter #(= (last %) timeout-form)
                                         (partition 2 1 (:generated-forms opts))))]
          (println (with-color :red "Response Timeout"))
          (println (with-color :red "Timeout while processing:"))
          (println (pr-str timeout-form))
          (println (with-color :red "Previous form was:"))
          (println (pr-str prev)))
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
            (cond (pos? ecnt)
                  (do (doseq [error errors]
                        (println "Expected:" (pr-str (:transit-expected error)))
                        (println "Actual:  " (pr-str (:transit-actual error)))))
                  (pos? wcnt)
                  (do (doseq [warn warnings]
                        (println "Expected:" (pr-str (:edn warn)))
                        (println "Actual:  " (pr-str (:data-actual warn)))
                        (println "Sent Transit:    " (pr-str (:transit-expected warn)))
                        (println "Received Transit:" (pr-str (:transit-actual warn))))))))))

(defn- run-test [project encoding opts]
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
              a))
          {}
          (partition 2 (partition-by #(.startsWith % "-") args))))

(defn -main [& args]
  (binding [*color* true]
    (verify-all (read-options args))
    (shutdown-agents)))

(ns exemplar
  (:require [transit.read :as r]
            [transit.write :as w]
            [clojure.java.io :as io]))

;; Generate a set of increasingly complex transit files.
;; Output checked into the transit repo under simple-examples.

(import [java.io File FileOutputStream ByteArrayInputStream ByteArrayOutputStream OutputStreamWriter])

(defn range-centered-on [n]
  (vec (range (- n 5) (+ n 6))))
  
(defn vmap [f s]
  (vec (map f s)))

(defn vector-of-keywords
  [n m]
  "Return a m length vector consisting of cycles of n keywordss"
  (vmap #(keyword (format "key%05d" %)) (take m (cycle (range n)))))

(defn map-of-size [n]
  (let [nums (range 0 n)]
    (apply
      sorted-map
      (interleave (map #(keyword (format "key%04d" %)) nums) nums))))
  
(defn write-description [file-name description vals]
  (println "##" description)
  (println "* Files:" (str file-name ".json") (str file-name ".mp"))
  (println "* Value (EDN)")
  (println)
  (doseq [item vals] (println "    " (pr-str item)))
  (println))

(defn write-exemplar [file-name description & vals]
  (write-description file-name description vals)
  (doseq [format [{:type :json, :suffix ".json"} {:type :msgpack :suffix ".mp"}]]
    (with-open [os (io/output-stream (str file-name (:suffix format)))]
      (let [jsw (w/writer os (:type format))]
        (doseq [item vals] (w/write jsw item))))))

(binding [*out* (io/writer "README.md")]
  (println "# Example transit files.\n\n")

  (write-exemplar "nil" "The nil/null/ain't there value" nil)
  (write-exemplar "true" "True" true)
  (write-exemplar "false" "False" false)
  (write-exemplar "zero" "Zero (integer)" 0)
  (write-exemplar "one" "One (integer)" 1)
  (write-exemplar "one_string" "A single string" "hello")
  (write-exemplar "one_keyword" "A single keyword" :hello)
  (write-exemplar "one_symbol" "A single symbol" 'hello)
  
  (def vector-simple  [1 2 3])
  (def vector-mixed  [0 1 2.0 true false "five" :six 'seven "~eight" nil])
  (def vector-nested [vector-simple vector-mixed])
  
  (write-exemplar "vector_simple" "A simple vector" vector-simple)
  (write-exemplar "vector_empty" "An empty vector" [])
  (write-exemplar "vector_mixed" "A ten element vector with mixed values" vector-mixed)
  (write-exemplar "vector_nested" "Two vectors nested inside of an outter vector" vector-nested)
  
  (def small-strings  ["" "a" "ab" "abc" "abcd" "abcde" "abcdef"])
  
  (write-exemplar "small_strings" "A vector of small strings" small-strings)
  
  (write-exemplar "strings_tilde" "A vector of strings containing ~" (vmap #(str "~" %) small-strings))

  (write-exemplar "strings_hash" "A vector of strings containing #" (vmap #(str "#" %) small-strings))
  
  (write-exemplar "small_ints" "A vector of eleven small integers" (range-centered-on 0))

  (apply write-exemplar "ints", "vector of ints" (range 128))
  
  (def interesting-ints 
    (vec
      (concat 
       (range-centered-on 32768)
       (range-centered-on 65536)
       (range-centered-on 2147483648)
       (range-centered-on 4294967296)
       (range-centered-on 2305843009213693952)
       (range-centered-on 4611686018427387904))))
  
  (write-exemplar 
    "ints_interesting"
    "A vector of possibly interesting positive integers"
    interesting-ints)
    
  (write-exemplar 
    "ints_interesting_neg"
    "A vector of possibly interesting negative integers"
    (vmap #(* -1 %) interesting-ints))
    
  (write-exemplar
    "doubles_small"
    "A vector of eleven doubles from -5.0 to 5.0"
    (vmap #(double %) (range-centered-on 0)))
  
  (write-exemplar
    "doubles_interesting"
    "A vector of interesting doubles"
    [-3.14159 3.14159 4E11 2.998E8 6.626E-34])
  
  
  (def symbols ['a 'ab 'abc 'abcd 'abcde 'a1 'b2 'c3 'a_b])
  
  (write-exemplar "symbols" "A vector of symbols" symbols)
  (write-exemplar "keywords" "A vector of keywords" (vmap keyword symbols))
  
  (write-exemplar "list_simple" "A simple list" (apply list vector-simple))
  (write-exemplar "list_empty" "An empty list" '())
  (write-exemplar "list_mixed" "A ten element list with mixed values" (apply list vector-mixed))
  (write-exemplar "list_mixed" "Two lists nested inside an outter list"
    (list (apply list vector-simple) (apply list vector-mixed)))

  (write-exemplar "set_simple" "A simple set" (set vector-simple))
  (write-exemplar "set_empty" "An empty set" #{})
  (write-exemplar "set_mixed" "A ten element set with mixed values" (set vector-mixed))
  (write-exemplar "set_mixed" "Two sets nested inside an outter set"
    (set [(set vector-simple) (set vector-mixed)]))
  

  (def map-simple {:a 1 :b 2 :c 3})
  (def map-mixed {:a 1 :b "a string" :c true})
  (def map-nested {:simple map-simple, :mixed map-mixed})
  
  (write-exemplar "map_simple" "A simple map" map-simple)
  (write-exemplar "map_mixed" "A mixed map" map-mixed)
  (write-exemplar "map_nested" "A nested map" map-nested)

  (write-exemplar "map_numeric_keys" "A map with numeric keys" {1 "one", 2 "two"})
 
  (write-exemplar "map_vector_keys" "A map with vector keys" {[1 1] "one", [2 2] "two"})
  
  (write-exemplar "map_10_items" "10 item map"  (map-of-size 10))
  
  (doseq [i [10 90 91 92 93 94 95]]
    (write-exemplar 
      (str "map_" i "_nested")
      (str "Map of two nested " i " item maps")
      {:f (map-of-size i) :s (map-of-size i)}))
  
  (write-exemplar 
    "maps_two_char_sym_keys"
    "Vector of maps with identical two char symbol keys"
    [{:aa 1 :bb 2} {:aa 3 :bb 4} {:aa 5 :bb 6}])
  
  (write-exemplar 
    "maps_three_char_sym_keys"
    "Vector of maps with identical three char symbol keys"
    [{:aaa 1 :bbb 2} {:aaa 3 :bbb 4} {:aaa 5 :bbb 6}])
  
  (write-exemplar 
    "maps_four_char_sym_keys"
    "Vector of maps with identical four char symbol keys"
    [{:aaaa 1 :bbbb 2} {:aaaa 3 :bbbb 4} {:aaaa 5 :bbbb 6}])
  
  (write-exemplar 
    "maps_two_char_string_keys"
    "Vector of maps with identical two char string keys"
    [{"aa" 1 "bb" 2} {"aa" 3 "bb" 4} {"aa" 5 "bb" 6}])
  
  (write-exemplar 
    "maps_three_char_string_keys"
    "Vector of maps with identical three char string keys"
    [{"aaa" 1 "bbb" 2} {"aaa" 3 "bbb" 4} {"aaa" 5 "bbb" 6}])
  
  (write-exemplar 
    "maps_four_char_string_keys"
    "Vector of maps with identical four char string keys"
    [{"aaaa" 1 "bbbb" 2} {"aaaa" 3 "bbbb" 4} {"aaaa" 5 "bbbb" 6}])

  (write-exemplar
    "vector_93_keywords_repeated_twice"
    "Vector of 93 keywords, repeated twice"
    (vector-of-keywords 93 186))

  (write-exemplar
    "vector_94_keywords_repeated_twice"
    "Vector of 94 keywords, repeated twice"
    (vector-of-keywords 94 188)))


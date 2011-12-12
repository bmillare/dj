(in-ns 'dj.toolkit)

(defprotocol Build
  (build [format data]))

(defprotocol Parse
  (parse [format txt]))

(defn replace-map
  "given an input string and a hash-map, returns a new string with all
  keys in map found in input replaced with the value of the key"
  [s m]
  (clojure.string/replace s
			  (re-pattern (apply str (interpose "|" (map #(java.util.regex.Pattern/quote %) (keys m)))))
			  m))

(defn re-find-all
  "given a regular expression re, and an input string txt, returns a vector of
  all sequential matches of re in txt"
  [^java.util.regex.Pattern re ^java.lang.CharSequence txt]
  (let [m (.matcher re txt)]
    (loop [matches []]
      (if (.find m)
	(recur (conj matches (.group m)))
	matches))))
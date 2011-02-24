(in-ns 'dj.toolkit)

(defprotocol Build
  (build [format data]))

(defprotocol Parse
  (parse [format txt]))

(defn replace-map [s m]
  (clojure.string/replace s
			  (re-pattern (apply str (interpose "|" (map #(java.util.regex.Pattern/quote %) (keys m)))))
			  m))

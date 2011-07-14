(ns dj.deps
  (:require [dj.cli])
  (:require [dj.io])
  (:require [dj.deps.core])
  (:require [dj.core :as core]))

(defn obtain-dependencies!
  "from list of all dependencies, recursively downloads, and installs
  remaining dependencies into local repositories, returns [src-paths
  jar-paths native-paths], where those elements are paths to files as
  strings"
  [dependencies options]
  (let [seen (ref #{})
	resolved (ref #{})
	src-paths (ref [])
	jar-paths (ref [])
	native-paths (ref [])
	exclusion-rules (ref (list
			      (fn [d] (= (:name d)
					 "clojure"))))
	exclude? (fn [d] (loop [r (seq @exclusion-rules)]
			   (when r
			     (if ((first r) d)
			       true
			       (recur (next r))))))
	letresolve! (fn resolve! [d]
		      (when (:verbose options)
			(when (exclude? d)
			  (println "excluding" d)))
		      (when-not (or (@resolved d) (exclude? d))
			(when (:verbose options)
			  (println "resolving" d))
			(if (@seen d)
			  (throw (Exception. (str "Circular dependency detected resolving " d)))
			  (let [obtained (dj.deps.core/obtain d options)]
			    (dosync (alter seen conj d)
				    (when-let [rules (dj.deps.core/exclusions d)]
				      (alter exclusion-rules concat rules)))
			    (doall (map resolve! (dj.deps.core/depends-on d)))
			    (dosync (alter seen disj d)
				    (alter resolved conj d)
				    (case (dj.deps.core/load-type d)
					  :src (alter src-paths conj obtained)
					  :jar (alter jar-paths conj obtained)
					  :native (let [[jars libs] obtained]
						    (alter jar-paths concat jars)
						    (alter native-paths concat libs))))))))]
    (doall (map letresolve! dependencies))
    [(map dj.io/string @src-paths)
     (map dj.io/string @jar-paths)
     (map dj.io/string @native-paths)]))
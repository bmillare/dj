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
			   (core/log r)
			   (when r
			     (if ((first r) d)
			       true
			       (recur (next r))))))
	letresolve! (fn resolve! [d]
		      (when (:verbose options)
			(println "resolving" d)
			(println "src-paths" @src-paths)
			(println "jar-paths" @jar-paths)
			(println "native-paths" @native-paths))
		      (when-not (or (@resolved d) (core/log (exclude? d)))
			(if (@seen d)
			  (throw (Exception. (str "Circular dependency detected resolving " d)))
			  (let [obtained (core/log (dj.deps.core/obtain d))]
			    (dosync (alter seen conj d)
				    (when-let [rules (dj.deps.core/exclusions d)]
				      (alter exclusion-rules concat rules)))
			    (doall (map resolve! (core/log (dj.deps.core/depends-on d))))
			    (dosync (alter seen disj d)
				    (alter resolved conj d)
				    (case (core/log (dj.deps.core/load-type d))
					  :src (alter src-paths conj obtained)
					  :jar (alter jar-paths conj obtained)
					  :native (alter native-paths conj obtained)))))))]
    (doall (map letresolve! dependencies))
    [(map dj.io/string (core/log @src-paths))
     (map dj.io/string (core/log @jar-paths))
     (map dj.io/string (core/log @native-paths))]))
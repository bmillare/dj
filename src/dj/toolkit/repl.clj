(in-ns 'dj.toolkit)

(defmacro log
  "for debugging, output code and code->val to stdout, returns val"
  [code]
  `(let [c# ~code]
     (prn '~code)
     (clojure.pprint/pprint c#)
     c#))

;; With all the namespaces loaded, find-doc can be overwhelming.
;; This is like find-doc, but just gives the associated names.

;; I want to filter vars on :name or :doc

'[list namespaces
  list what namespace publics and refers
  unify interfaces, accept strings or symbols
  find varname from matching string in documentation
  find doc from matching string in documentation
  find source file
  list java methods
  list java method names
  list classpath
  list current directory]

(defn ns-names
  "returns namespaces with name that matches re"
  ([] (all-ns))
  ([string-or-re]
     (let [re (re-pattern string-or-re)]
       (for [ns (all-ns)
	     :when (re-find re (str (ns-name ns)))]
	 ns))))

(defn ns-vars
  "list vars (interns, publics, refers) from ns"
  [command & [ns]]
  (let [ns (the-ns ns)]
    (case command
	  :all (all-ns)
	  :interns (vals (ns-interns ns))
	  :publics (vals (ns-publics ns))
	  :refers (vals (ns-refers ns)))))

(defn meta-table [ns & [command]]
  (let [ns (the-ns ns)]
    (set (map meta (vals (case command
			       :interns (ns-interns ns)
			       :publics (ns-publics ns)
			       :refers (ns-refers ns)
			       (ns-interns ns)))))))

(defn list-var-names-from-name-or-doc
  "returns seq of vars that match re in name or doc string"
  [re vars]
  (for [v (sort-by (comp :name meta) vars)
	:when (or (re-find re (str (:name (meta v))))
		  (when-let [doc-data (:doc (meta v))]
		    (re-find re doc-data)))]
    v))

(defn list-var-names-from-name
  "returns seq of vars that match re in name only"
  [re vars]
  (for [v (sort-by (comp :name meta) vars)
	:when (re-find re (str (:name (meta v))))]
    v))

(defn var-apropos
  "returns vars in ns matching re in doc or name"
  ([]
     (vals (ns-interns *ns*)))
  ([var-sym-string-or-pattern & [ns]]
     (let [make-str-re (fn [x]
			 (if (symbol? x)
			   (if-let [the-var (resolve x)]
			     (str (:name (meta the-var)))
			     (str x))
			   (if (or (string? x)
				   (= (class x) java.util.regex.Pattern))
			     x
			     (throw (Exception. "Type of first arg must be regex, string, or symbol")))))
	   ns (if ns
		(if (keyword? ns)
		  ns
		  (the-ns ns))
		:this)]
       (list-var-names-from-name-or-doc
	(re-pattern (make-str-re var-sym-string-or-pattern))
	(case ns
	      :all (mapcat #(vals (ns-interns %)) (all-ns))
	      :this (vals (ns-interns *ns*))
	      (vals (ns-interns ns)))))))

(defn var-names
  "returns vars in ns matching re in doc or name"
  ([]
     (vals (ns-interns *ns*)))
  ([var-sym-string-or-pattern & [ns]]
     (let [make-str-re (fn [x]
			 (if (symbol? x)
			   (if-let [the-var (resolve x)]
			     (str (:name (meta the-var)))
			     (str x))
			   (if (or (string? x)
				   (= (class x) java.util.regex.Pattern))
			     x
			     (throw (Exception. "Type of first arg must be regex, string, or symbol")))))
	   ns (if ns
		(if (keyword? ns)
		  ns
		  (the-ns ns))
		:this)]
       (list-var-names-from-name
	(re-pattern (make-str-re var-sym-string-or-pattern))
	(case ns
	      :all (mapcat #(vals (ns-interns %)) (all-ns))
	      :this (vals (ns-interns *ns*))
	      (vals (ns-interns ns)))))))

(defn print-doc [v]
  (println "-------------------------")
  (println (str (ns-name (:ns (meta v))) "/" (:name (meta v))))
  (prn (:arglists (meta v)))
  (when (:macro (meta v))
    (println "Macro"))
  (println " " (:doc (meta v))))

(defn var-docs
  "Prints documentation for any var whose documentation or name
 contains a match for re-string-or-pattern in ns"
  ([]
     (map print-doc (vals (ns-interns *ns*))))
  ([re-string-or-pattern & [ns]]
     (let [re (re-pattern re-string-or-pattern)]
       (doseq [ns (case ns
			:all (all-ns)
			:this [*ns*]
			[(the-ns (or ns *ns*))])
	       v (sort-by (comp :name meta) (vals (ns-interns ns)))
	       :when (or (re-find re (str (:name (meta v))))
			 (when-let [doc-data (:doc (meta v))]
			   (re-find re doc-data)))]
	 (print-doc v)))))

(defn unmap-ns [ns]
  (doseq [s (filter (complement #{'unmap-ns})
		    (map first (ns-interns ns)))]
    (ns-unmap ns s)))

(defmacro var-src
  "alias for clojure.repl/source"
  [arg]
  `(clojure.repl/source ~arg))

(defn javadoc
  [class-or-object]
  (clojure.java.javadoc/javadoc class-or-object))

(defn classpaths
  []
  (dj.classloader/get-classpaths user/*classloader*))

(defn pwd
  []
  (.getCanonicalPath (java.io.File. ".")))
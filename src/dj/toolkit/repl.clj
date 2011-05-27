(in-ns 'dj.toolkit)

(defmacro log
  "for debugging, output code and code->val to stdout, returns val"
  [code]
  `(let [c# ~code]
     (prn '~code)
     (clojure.pprint/pprint c#)
     c#))

(defn- add-log-calls [code]
  (if (seq? code)
    `(log ~(map add-log-calls code))
    code))

(defmacro r-log [code]
  (add-log-calls code))

;; SPEC: Needs to accomplish
#_ [list namespaces
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

(defn- print-doc [m]
  (println "-------------------------")
  (println (str (when-let [ns (:ns m)] (str (ns-name ns) "/")) (:name m)))
  (cond
   (:forms m) (doseq [f (:forms m)]
		(print "  ")
		(prn f))
   (:arglists m) (prn (:arglists m)))
  (if (:special-form m)
    (do
      (println "Special Form")
      (println " " (:doc m))
      (if (contains? m :url)
	(when (:url m)
	  (println (str "\n  Please see http://clojure.org/" (:url m))))
	(println (str "\n  Please see http://clojure.org/special_forms#"
		      (:name m)))))
    (do
      (when (:macro m)
	(println "Macro"))
      (println " " (:doc m)))))

(defn var-docs
  "Returns documentation for any var whose documentation or name
 contains a match for re-string-or-pattern in ns"
  ([]
     (map #(with-out-str (print-doc (meta %))) (vals (ns-interns *ns*))))
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
	 (with-out-str (print-doc (meta v)))))))

(defn unmap-ns
  "unmap everything from a ns"
  [ns]
  (doseq [s (filter (complement #{'unmap-ns})
		    (map first (ns-interns ns)))]
    (ns-unmap ns s)))

(defn var-src-fn [sym]
  (when-let [v (resolve sym)]
    (if-let [src (:src (meta v))]
      src
      (clojure.repl/source-fn sym))))

(defmacro var-src
  "includes core source generation and special evaluator version"
  [arg]
  `(println (var-src-fn '~arg)))

(defn javadoc
  [class-or-object]
  (clojure.java.javadoc/javadoc class-or-object))

(defn classpaths
  []
  (dj.classloader/get-classpaths user/*classloader*))

(defn pwd
  []
  (.getCanonicalPath (java.io.File. ".")))

(defn protocol? [maybe-p]
  (boolean (:on-interface maybe-p)))

;; not super efficient, is there a way to automatically know which protocols it implements?
(defn all-protocols [& [ns]]
  (filter #(protocol? @(val %)) (ns-publics (if ns (find-ns ns) *ns*))))

(defn implemented-protocols [instance ns]
  (filter #(satisfies? @(val %) instance) (all-protocols ns)))

(defn doc-ns [ns]
  (let [ns (the-ns ns)
	interns (ns-vars :publics ns)]
    (apply str
	   "Name: " (ns-name ns) "\n"
	   "Publics: " (count interns) " vars\n\n"
	   (interpose "\n" (sort (map #(:name (meta %)) interns))))))

#_ (defn load-string-as [txt line file ns]
     (let [padded-txt (apply str (take line (repeat "\n")))
	   rdr (-> (java.io.StringReader. txt)
		   (clojure.lang.LineNumberingPushbackReader.))]
       (. clojure.lang.Compiler (load rdr full-path file-name))
       (alter-meta! the-var assoc :src source-code)))

(defn all-threads
  "Get a seq of the current threads."
  []
  (let [grp (.getThreadGroup (Thread/currentThread))
	cnt (.activeCount grp)
	ary (make-array Thread cnt)]
    (.enumerate grp ary)
    (seq ary)))

(defn top-threads
  "Return a seq of threads sorted by their total userland CPU usage."
  []
  (let [mgr (java.lang.management.ManagementFactory/getThreadMXBean)
	cpu-times (map (fn [t]
			 [(.getThreadCpuTime mgr (.getId t)) t])
		       (all-threads))]
    (map
     (fn [[cpu t]] [cpu (.getName t) (.getId t) t])
     (reverse (sort-by first cpu-times)))))
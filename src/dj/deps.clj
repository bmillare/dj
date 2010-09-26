(ns dj.deps
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [dj.repository :as repo])
  (:require [dj.cli])
  (:require [dj.core :as core]))

(def options {:pretend nil
	      :verbose true
	      :version-fn {}})

(defn exclude-exact
  "dependency must match exactly to be excluded, group-id/artifact-id and version"
  [exclusions d]
  (exclusions d))

(defn exclude-id
  "dependency group-id/artifact-id must match to be excluded"
  [exclusions d]
  (first (filter #(= (first d)
		     (first %))
		 exclusions)))

(defn- condense-recursive
  "given output from clojure.xml/parse, returns same tree but
   condensed to just the tag and content"
  [{:keys [tag content] :as xml-map-entry}]
  (if (vector? content)
    (if (:tag (first content))
      {tag (map condense-recursive content)}
      {tag content})
    (if (:tag content)
      {tag (condense-recursive content)}
      {tag content})))

(defn- find-entry
  "lazy linear search"
  [k p]
  (k (first (filter k p))))

(defn- extract-dependency
  "given dependency parse returns dependency form"
  [parse-entry]
  [(symbol (first (:groupId parse-entry))
	   (first (:artifactId parse-entry)))
   (first (:version parse-entry))])

(defn- extract-exclusions
  "given dependency parse returns exclusions"
  [parse-entry]
  (set (for [{exclusion :exclusion} (:exclusions parse-entry)]
	 (extract-dependency (apply merge exclusion)))))

(defn- get-direct-dependencies-parse!
  "takes dependency form and returns the parsed form sequence of
   dependency forms that directly fulfill given dependency

   downloads dependencies to repository if needed"
  [dependency]
  (repo/download-dependency! dependency (:pretend options))
  (->> (repo/get-dependency-path dependency ".pom")
       xml/parse 
       condense-recursive
       :project
       (find-entry :dependencies)))

(defn- qualify-dependency
  "returns fully qualified dependency

   needed because sometimes dependencies listed in project.clj are not
  always fully qualified, or the pom file has macros"
  [dependency]
  (let [id (first dependency)
	v (second dependency)]
   [(symbol (or (namespace id)
		(name id))
	    (name id))
    (or ((:version-fn options) v)
	v)]))

(defn get-direct-dependencies!
  "from input dependency form, create list of direct dependencies and
  update exclusions

  also takes hook function that must accept dependency-map and
  dependency-form"
  [d hook]
  (for [{dependency :dependency} (get-direct-dependencies-parse! d)]
    (let [dependency-map (apply merge dependency)
	  dependency-form (extract-dependency dependency-map)]
      (hook dependency-map dependency-form)
      dependency-form)))

;; hooks, enable customizable handling of dependency resolution
;; defined to potentially have the following methods:
;; (predicate [dependency] "operate on current node?")
;; (pre [dependency] "run before traversal of dependency")
;; (dependency [dependency-map dependency] "run during traversal of dependency, passed dependency map and form")
;; (post [dependency] "run after traversal of dependency")

;; hooks are defined to do their work only by side-effects

(defn resolved-hook
  "determine if dependency was already resolved"
  [data resolved-fn]
  (let [data (ref (set data))]
    {:predicate (fn [dependency]
		  (resolved-fn @data dependency))
     :post (fn [dependency]
	     (dosync (alter data conj dependency)))}))

(defn exclude-hook
  "determine if dependency needs to be excluded"
  [data exclude-fn]
  (let [data (ref (set (conj data ['org.clojure/clojure nil])))]
    {:predicate (fn [dependency]
		  (let [result (exclude-fn @data dependency)]
		    (when (and result
			       (:verbose options))
		      (println "excluding " dependency))
		    result))
     :dependency (fn [dependency-map _]
		   (dosync (alter data set/union (extract-exclusions dependency-map))))}))

(defn optional-hook
  "may be useful in the future"
  []
  {:dependency (fn [dependency-map dependency]
		 (when (and (:verbose options)
			    (:optional dependency-map))
		   (println "resolving optional " dependency)))})

(defn get-methods-code [keyword-name hooks]
  `(for [{method# ~keyword-name} ~hooks :when method#] method#))

(defn make-hook-fn-form [bindings fn-args fn-body]
  `(let ~bindings
     (fn [~@fn-args]
       ~fn-body)))

(defmacro make-hook-fn-code [keyword-name hooks fn-args fn-list-expansion-sym fn-body]
  `(let [methods-sym# (gensym "methods")
	 method-syms# (for [{method# ~keyword-name} ~hooks :when method#]
			(gensym))
	 bindings# (vec (concat [methods-sym# (get-methods-code ~keyword-name ~hooks)]
				(apply concat
				       (for [[s# n#] (partition 2 (interleave method-syms# (range)))]
					 (list s# (list 'clojure.core/nth methods-sym# n#))))))
	 ~fn-list-expansion-sym (for [ms# method-syms#]
				  (concat (list ms#) '~fn-args))]
     (make-hook-fn-form bindings# '~fn-args ~fn-body)))

(defn make-hook-fns*
  "aggregates all hooks into a single compiled function for speed"
  [hooks]
  {:predicate (make-hook-fn-code :predicate hooks [d] fn-list-exp `(or ~@fn-list-exp))
   :pre (make-hook-fn-code :pre hooks [d] fn-list-exp `(do ~@fn-list-exp))
   :dependency (make-hook-fn-code :dependency hooks [d-map d] fn-list-exp `(do ~@fn-list-exp))
   :post (make-hook-fn-code :post hooks [d] fn-list-exp `(do ~@fn-list-exp))})

(defn make-hook-fns
  "aggregates all hooks into a single function"
  [hooks]
  {:predicate (fn [d]
		(some (fn [m] (m d))
		      (for [{method :predicate} hooks :when method]
			method)))
   :pre (fn [d] (doseq [{method :pre } hooks :when method]
		  (method d)))
   :dependency (fn [d-map d] (doseq [{method :dependency} hooks :when method]
		  (method d-map d)))
   :post (fn [d] (doseq [{method :post} hooks :when method]
		  (method d)))})

(defn get-all-dependencies!
  "recursively determines and returns all dependencies for items in
  dependency-list. Preserves order, removes redundancies, raises
  errors on cyclic dependencies. Supports optional exclusions, a
  collection of dependencies that are not to be resolved.

  Now supports generic hooks to extend functionality

  TODO:
  -add support for excluding on just artifactid
  -handle ${extrastuff} forms in xml file
  -add support for source dependencies in the live repository
  -add support for optional dependencies

  algorithm is just modify data structures during traversal of
  dependency tree"
  [dependency-list & opts]
  (binding [options (apply merge options opts)]
    (let [seen (ref #{})  ;; circular dependency detection
	  result (ref []) ;; preserve order
	  {:keys [modify predicate pre dependency post]} (make-hook-fns (:hooks options))]
      (letfn [(resolve-dependency
	       ;; walk tree
	       [d]
	       (let [d (qualify-dependency d)]
		 (when (:verbose options) (println "resolving " d))
		 (when-not (predicate d)
		   (if (@seen d)
		     (throw (Exception. "Circular dependency detected"))
		     (do
		       (dosync (alter seen conj d))
		       (pre d)
		       (doall (map resolve-dependency
				   (get-direct-dependencies! d
							     dependency)))
		       (dosync
			(alter seen disj d)
			(alter result conj d))
		       (post d))))))]
	(doseq [dependency dependency-list]
	  (resolve-dependency dependency))
	@result))))

#_ (defn get-all-dependencies-fix!
  "recursively determines and returns all dependencies for items in
  dependency-list. Preserves order, removes redundancies, raises
  errors on cyclic dependencies. Supports optional exclusions, a
  collection of dependencies that are not to be resolved.

  Now supports generic hooks to extend functionality

  TODO:
  -add support for excluding on just artifactid
  -handle ${extrastuff} forms in xml file
  -add support for source dependencies in the live repository
  -add support for optional dependencies

  algorithm is just modify data structures during traversal of
  dependency tree"
  [project-name]
  (binding [options (apply merge options {})]
    (let [seen (ref #{})  ;; circular dependency detection
	  result (ref []) ;; preserve order
	  src-files (ref [])
	  native-files (ref [])
	  {:keys [modify predicate pre dependency post]} (make-hook-fns (:hooks options))]
      (letfn [(resolve-src-dependency
	       [name]
	       (let [project-data (->
				   name
				   dj.cli/project-name-to-file
				   dj.cli/read-project)]
		 ;; pre,post,etc functions do not work on names
		 (doseq [n (:include-projects project-data)]
		   (resolve-src-dependency n))
		 ;; get-all-native will need to turn to hooks to do seens
		 ;; (get-all-native! (:native-dependencies project-data))
		 (doseq [d (:dependencies project-data)]
		   (resolve-dependency d))))
	      (resolve-dependency
	       ;; walk tree
	       [d]
	       (let [d (qualify-dependency d)] ;; modify qualify to understand src dependencies
		 (when (:verbose options) (println "resolving " d))
		 (when-not (predicate d)
		   (if (@seen d)
		     (throw (Exception. "Circular dependency detected"))
		     (do
		       (dosync (alter seen conj d))
		       (pre d)
		       (doall (map resolve-dependency ;; case statement to do different things with native and src deps
				   (get-direct-dependencies! d
							     dependency)))
		       (dosync
			(alter seen disj d)
			(alter result conj d))
		       (post d))))))]
	(resolve-src-dependency project-name)
	{:src src-files
	 :jar (for [d @result] (repo/get-dependency-path d ".jar"))
	 :native (seq (set (map #(.getParentFile %) native-files)))}))))

(defn unjar [#^java.io.File jar-file install-dir]
  (let [jar-file (java.util.jar.JarFile. jar-file)]
   (for [entry (enumeration-seq (.entries jar-file))
	 :let [f (repo/file install-dir (.getName entry))]]
     (if (.isDirectory entry)
       (.mkdirs f)
       (with-open [in-stream (.getInputStream jar-file entry)
		   out-stream (java.io.FileOutputStream. f)]
	 (loop []
	   (when (> (.available in-stream)
		    0)
	     (.write out-stream (.read in-stream))
	     (recur))))))))

(defn get-native!
  "resolve and install native dependency"
  [dependency]
  ;; property for
  ;; os.name  platforms
  ;; os.arch  architectures
  (let [platforms {"Linux" "linux"
		   "Mac OS X" "macosx"
		   "Windows" "windows"
		   "SunOS" "solaris"}
	architectures {"amd64" "x86_64"
		       "x86_64" "x86_64"
		       "x86" "x86"
		       "i386" "x86"
		       "arm" "arm"
		       "sparc" "sparc"}
	install-dir (repo/file core/system-root
			       "./usr/native/"
			       (repo/get-dependency-path-prefix dependency))]
    (if (.exists install-dir)
      (println install-dir "exists, skipping")
      (prn (unjar (repo/file (repo/download-dependency! dependency))
		  install-dir)))
    [(seq (.listFiles (repo/file install-dir "./lib/")))
     (seq (.listFiles (repo/file install-dir "./native/"
				 (platforms (System/getProperty "os.name"))
				 (architectures (System/getProperty "os.arch")))))]))

(defn transpose
  "nested vectors matrix"
  [m]
  (vec (apply map vector m)))

(defn get-all-native!
  "resolve all native dependencies"
  [dependency-list]
  ;; property for
  ;; os.name  platforms
  ;; os.arch  architectures
  (when dependency-list
    (let [[[jars] [libs]] (transpose (doall
				      (for [dependency dependency-list]
					(get-native! dependency))))]
      {:jars jars
       :libs libs})))


(ns dj.deps
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [dj.repository :as repo]))

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

(defprotocol hook
  "enable customizable handling of dependency resolution"
  (predicate [this dependency] "operate on current node?")
  (pre [this dependency] "run before traversal of dependency")
  (dependency [this dependency-map dependency] "run during traversal of dependency, passed dependency map and form")
  (post [this dependency] "run after traversal of dependency"))

(defn resolved-hook
  "determine if dependency was already resolved"
  [data resolved-fn]
  (let [data (ref (set data))]
    (reify hook
	   (predicate [this dependency]
		      (resolved-fn @data dependency))
	   (pre [this dependency] nil)
	   (dependency [this dependency-map dependency] nil)
	   (post [this dependency]
		 (dosync (alter data conj dependency))))))

(defn exclude-hook
  "determine if dependency needs to be excluded"
  [data exclude-fn]
  (let [data (ref (set (conj data ['org.clojure/clojure nil])))]
    (reify hook
	   (predicate [this dependency]
		      (let [result (exclude-fn @data dependency)]
			(when (and result
				   (:verbose options))
			  (println "excluding " dependency))
			result))
	   (pre [this dependency] nil)
	   (dependency [this dependency-map _]
		       (dosync (alter data set/union (extract-exclusions dependency-map))))
	   (post [this dependency] nil))))

(defn optional-hook
  "may be useful in the future"
  []
  (reify hook
	 (predicate [this dependency] nil)
	 (pre [this dependency] nil)
	 (dependency [this dependency-map dependency]
		     (when (and (:verbose options)
				(:optional dependency-map))
		       (println "resolving optional " dependency)))
	 (post [this dependency] nil)))

(defmacro do-hooks [method hooks & args]
  (case (name method)
	"predicate"
	`(some (fn [h#] (~method h# ~@args))
	       ~hooks)
	"dependency"
	`(fn [dependency-map# dependency#]
	   (doseq [h# ~hooks]
	     (~method h# dependency-map# dependency#)))
	`(doseq [h# ~hooks]
	   (~method h# ~@args))))

(defn get-all-dependencies!
  "recursively determines and returns all dependencies for items in
  dependency-list. Preserves order, removes redundancies, raises
  errors on cyclic dependencies. Supports optional exclusions, a
  collection of dependencies that are not to be resolved

  TODO:
  -add support for excluding on just artifactid
  -handle ${extrastuff} forms in xml file
  -add support for source dependencies in the live repository
  -add support for optional dependencies

  algorithm is just modify data structures during traversal of
  dependency tree"
  [dependency-list & opts]
  (binding [options (apply merge options opts)]
   (let [seen (ref #{})          ;; circular dependency detection
	 result (ref [])         ;; preserve order
	 hooks (:hooks options)]
     (letfn [(resolve-dependency
	      ;; walk tree
	      [d]
	      (let [d (qualify-dependency d)]
		(when (:verbose options) (println "resolving " d))
		(when-not (do-hooks predicate hooks d)
		  (if (@seen d)
		    (throw (Exception. "Circular dependency detected"))
		    (do
		      (dosync (alter seen conj d))
		      (do-hooks pre hooks d)
		      (doall (map resolve-dependency
				  (get-direct-dependencies! d
							    (do-hooks dependency hooks d))))
		      (dosync
		       (alter seen disj d)
		       (alter result conj d))
		      (do-hooks post hooks d))))))]
       (doseq [dependency dependency-list]
	 (resolve-dependency dependency))
       @result))))
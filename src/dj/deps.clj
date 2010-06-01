(ns dj.deps
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [dj.repository :as repo]))

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

(def options {:pretend nil
	      :verbose true
	      :exclusion-fn exclude-id
	      :resolved-fn exclude-id
	      :version-fn {}})

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

  algorithm is just modify two sets and list during a post order
  traversal of the dependency tree"
  ([dependency-list exclusions]
     (let [;; circular dependency detection
	   seen (ref #{})
	   ;; needed to preserve order
	   resolved (ref [])
	   resolved-set (ref #{})
	   exclusions (ref (set exclusions))
	   ;; may want to do something with this in the future
	   optional (ref #{})]
       (letfn [(get-direct-dependencies!
		;; create list of direct dependencies and update exclusions
		[d]
		(for [{dependency :dependency} (get-direct-dependencies-parse! d)]
		  (let [dependency-map (apply merge dependency)
			dependency-form (extract-dependency dependency-map)]
		    (dosync (alter exclusions set/union (extract-exclusions dependency-map)))
		    (when (and (:verbose options)
			       (:optional dependency-map))
		      (println "resolving optional " dependency-form))
		    (dosync (alter optional conj dependency-form))
		    dependency-form)))
	       (resolve-dependency
		;; walk tree
		[d]
		(let [d (qualify-dependency d)]
		  (when (:verbose options) (println "resolving " d))
		  (when-not (or ((:resolved-fn options) @resolved-set d)
				(let [result ((:exclusion-fn options) @exclusions d)]
				  (when (and result
					     (:verbose options))
				    (println "excluding " d))
				  result))
		    (if (@seen d)
		      (throw (Exception. "Circular dependency detected"))
		      (do
			(dosync (alter seen conj d))
			(doall (map resolve-dependency (get-direct-dependencies! d)))
			(dosync
			 (alter seen disj d)
			 (alter resolved conj d)
			 (alter resolved-set conj d)))))))]
	 (doseq [dependency dependency-list]
	   (resolve-dependency dependency))
	 @resolved)))
  ([dependency-list]
     (get-all-dependencies! dependency-list nil)))

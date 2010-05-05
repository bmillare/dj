(ns dj.deps
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [dj.repository :as repo]))

(def options {:pretend nil
	      :verbose true})

(defn- condense-recursive [{:keys [tag content] :as xml-map-entry}]
  "given output from clojure.xml/parse, returns same tree but
   condensed to just the tag and content"
  (if (vector? content)
    (if (:tag (first content))
      {tag (map condense-recursive content)}
      {tag content})
    (if (:tag content)
      {tag (condense-recursive content)}
      {tag content})))

(defn- find-entry [k p]
  "lazy linear search"
  (k (first (filter k p))))

(defn- extract-dependency [parse-entry]
  "given dependency parse returns dependency form"
  [(symbol (first (:groupId parse-entry))
	   (first (:artifactId parse-entry)))
   (first (:version parse-entry))])

(defn- extract-exclusions [parse-entry]
  "given dependency parse returns exclusions"
  (set (for [{exclusion :exclusion} (:exclusions parse-entry)]
	 (extract-dependency (apply merge exclusion)))))

(defn- get-direct-dependencies-parse! [dependency]
  "takes dependency form and returns the parsed form sequence of
   dependency forms that directly fulfill given dependency

   downloads dependencies to repository if needed"
  (repo/download-dependency! dependency (:pretend options))
  (->> (repo/get-dependency-path dependency ".pom")
       xml/parse 
       condense-recursive
       :project
       (find-entry :dependencies)))

(defn get-all-dependencies!
  "recursively deterimines and returns all dependencies for items in
  dependency-list. Preserves order, removes redundancies, raises
  errors on cyclic dependencies. Supports optional exclusions, a
  collection of dependencies that are not to be resolved

  TODO:
  -add support for excluding on just artifactid

  algorithm is just modify two sets and list during a post order
  traversal of the dependency tree"
  ([dependency-list exclusions]
     (let [seen (ref #{})
	   resolved (ref [])
	   resolved-set (ref #{})
	   exclusions (ref (set exclusions))]
       (letfn [(get-direct-dependencies!
		[d]
		"create list of direct dependencies and update exclusions"
		(for [{dependency :dependency} (get-direct-dependencies-parse! d)]
		  (let [dependency-map (apply merge dependency)]
		    (dosync (alter exclusions set/union (extract-exclusions dependency-map)))
		    (extract-dependency dependency-map))))
	       (resolve-dependency
		[d]
		"walk tree"
		(when-not (or (@resolved-set d)
			       (@exclusions d))
		  (if (@seen d)
		    (throw (Exception. "Circular dependency detected"))
		    (do
		      (dosync (alter seen conj d))
		      (doall (map resolve-dependency (get-direct-dependencies! d)))
		      (dosync
		       (alter seen disj d)
		       (alter resolved conj d)
		       (alter resolved-set conj d))))))]
	 (doseq [dependency dependency-list]
	   (resolve-dependency dependency))
	 @resolved)))
  ([dependency-list]
     (get-all-dependencies! dependency-list nil)))

(defn testo []
  nil)

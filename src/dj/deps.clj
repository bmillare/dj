(ns dj.deps
  (:require [clojure.xml :as xml])
  (:require [dj.repository :as repo]))

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

(defn- find-entry [k m]
  (k (first (filter k m))))

(defn- extract-dependency% [{parse-entry :dependency }]
  "given dependency section of condensed parse of pom xml data,
   returns dependency form"
  (when parse-entry
    [(symbol (first (find-entry :groupId parse-entry))
	     (first (find-entry :artifactId parse-entry)))
     (first (find-entry :version parse-entry))]))

(defn- extract-dependency [parse-entry]
  "given dependency parse returns dependency form"
  (when parse-entry
    [(symbol (first (find-entry :groupId parse-entry))
	     (first (find-entry :artifactId parse-entry)))
     (first (find-entry :version parse-entry))]))

(defn- extract-exclusions [dependencies-parse]
  (->> dependencies-parse
       :dependency
       (find-entry :exclusions)
       ;;
       (map extract-dependency)))

(defn- get-direct-dependencies-parse [dependency]
  "takes dependency form and returns the parsed form sequence of
   dependency forms that directly fulfill given dependency

   Assumes pom file exists"
  (->> (xml/parse (repo/get-dependency-path dependency ".pom"))
       condense-recursive
       :project
       (find-entry :dependencies)))

(defn extract-direct-dependencies [dependecies-parse]
  (map extract-dependency dependencies-parse))

(defn get-direct-dependencies [dependency]
  "takes dependency form and returns sequence of dependency forms that
   directly fulfill given dependency
   Assumes pom file exists"
  (extract-direct-dependencies (get-direct-dependencies-parse dependency)))

(defn get-all-dependencies
  "recursively deterimines and returns all dependencies for items in
  dependency-list. Preserves order, removes redundancies, raises
  errors on cyclic dependencies. Optional exclusions argument should
  be a collection of dependencies that are not to be included

  TODO:
  -add support for same package name conflict
  -add exclusions

  algorithm is just modify two sets during a post order traversal of
  the dependency tree"
  ([dependency-list exclusions]
     (let [seen (ref #{})
	   resolved (ref #{})
	   exclusions (ref (set exclusions))]
       (letfn [(resolve-dependency
		"walk tree"
		[d]
		(when-not (@resolved d)
		  (if (@seen d)
		    (throw (Exception. "Circular dependency detected"))
		    (do
		      (dosync (alter seen conj d))
		      (let [dependencies-parse (get-direct-dependencies-parse d)
			    ]
			;;loop through dependencies
			(doseq [{dependency-parse :dependency} dependencies-parse]
			  (extract-dependency dependency-parse)
			  (extract-exclusion (:depend)))
			;;extract dep-form from dep
			;;add ex from dep
			)
		      
		      (doall (map resolve-dependency (get-direct-dependencies d)))
		      (dosync
		       (alter seen disj d)
		       (alter resolved conj d))))))]
	 (doseq [dependency dependency-list]
	   (resolve-dependency dependency))
	 @resolved)))
  ([dependency-list]
     (get-all-dependencies dependency-list nil)))

(defn resolve-dependencies []
  "given dependency form, returns list of dependency forms that the input depends on

expect to implement this as a recursive analyzing of pom file")

(defn testo []

  ;(get-all-dependencies [['org.clojure/clojure "1.1.0"]])
  ;(get-all-dependencies [['org.clojure/clojure-contrib "1.1.0"]])
  (get-all-dependencies [['leiningen/leiningen "1.0.0-SNAPSHOT"]])
  ;;(get-direct-dependencies ['org.clojure/clojure-contrib "1.1.0"])
  )
;; defn obtain all dependencies for artifact, includes exclusion
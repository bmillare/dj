(ns dj.deps.project
  (:require [dj.deps.native])
  (:require [dj.deps.maven])
  (:use [dj.toolkit :only [new-file str-path mkdir get-path]])
  (:use [dj.deps.core :only [ADependency parse]])
  (:use [clojure.java.shell :only [sh]])
  (:use [dj.core :only [system-root]]))

(defn read-project
  "in directory, looks for file project.clj and returns the result of
  running that file in the context of the dj.cli namespace"
  [#^java.io.File directory]
  (let [old-ns (ns-name *ns*)]
    (in-ns 'dj.core)
    (let [p (load-file (.getCanonicalPath (new-file directory "project.clj")))]
      (in-ns old-ns)
      p)))

(defn project-name-to-file [project-name]
  (new-file system-root "usr/src/" project-name))

(defrecord project-dependency [name])

(defrecord git-dependency [name git-path]
  ADependency
  (obtain [d _]
	  (let [f (new-file system-root "usr/src" name "src")]
	    (when-not (.exists f)
	      (sh "git" "clone" git-path
		  :dir (get-path (new-file system-root "usr/src/"))))
	    f))
  (depends-on [d]
	      (let [project-data (read-project (project-name-to-file name))
		    src-deps (map #(parse % :project-dependency) (:src-dependencies project-data))
		    jar-deps (map parse (:dependencies project-data))
		    native-deps (map #(parse % :native-dependency) (:native-dependencies project-data))]
		(concat src-deps jar-deps native-deps)))
  (load-type [d] :src)
  (exclusions [d]
	      (let [exclusion-data (-> name
				       project-name-to-file
				       read-project
				       :exclusions)]
		(map #(parse % :project-exclusion) exclusion-data))))

(defrecord source-contrib-dependency [name])

(defmethod parse :project-exclusion [obj & [_]]
	   (let [id (first obj)
		 name (name id)
		 group (or (namespace id)
			   (clojure.core/name id))
		 version (second obj)]
	     (fn [d] (and (= (:group d) group)
			  (= (:name d) name)
			  (= (:version d) version)))))

(defmethod parse :project-dependency [name & [_]]
	   (if (re-find #"http://|git://|https://" name)
	     (let [[_ n] (re-find #"(\w+)\.git" (last (.split #"/" name)))]
	       (git-dependency. n name))
	     (if (= "clojure" (first (.split #"/" name)))
	       (source-contrib-dependency. name)
	       (project-dependency. name))))

(extend project-dependency
  ADependency
  {:obtain (fn [d _]
	     (new-file system-root "usr/src" (:name d) "src"))
   :depends-on (fn [d]
		 (let [project-data (read-project (project-name-to-file (:name d)))
		       src-deps (map #(parse % :project-dependency) (:src-dependencies project-data))
		       jar-deps (map parse (:dependencies project-data))
		       native-deps (map #(parse % :native-dependency) (:native-dependencies project-data))]
		   (concat src-deps jar-deps native-deps)))
   :load-type (fn [d] :src)
   :exclusions (fn [d]
		 (let [exclusion-data (-> (:name d)
					  project-name-to-file
					  read-project
					  :exclusions)]
		   (map #(parse % :project-exclusion) exclusion-data)))})

(defn pass-pom-data
  "grabs from cache if possible"
  [d f]
  (-> (new-file system-root "usr/src" (:name d) "pom.xml")
      clojure.xml/parse
      dj.deps.maven/condense-xml
      f))

(extend source-contrib-dependency
  ADependency
  {:obtain (fn [d _]
	     (let [n (:name d)
		   f (new-file system-root "usr/src" n "src/main/clojure")]
	       (when-not (.exists f)
		 (let [clojure-folder (new-file system-root "usr/src/clojure")]
		   (when-not (.exists clojure-folder)
		     (mkdir clojure-folder)))
		 (sh "git" "clone" (str "git://github.com/" n ".git")
		     :dir (get-path (new-file system-root "usr/src/clojure"))))
	       f))
   :depends-on (fn [d]
		 (pass-pom-data d dj.deps.maven/pom-extract-dependencies))
   :load-type (fn [d] :src)
   :exclusions (fn [d]
		 (pass-pom-data d dj.deps.maven/pom-extract-exclusions))})
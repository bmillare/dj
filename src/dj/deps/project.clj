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

(defn extract-project-dependencies [name]
  (let [project-data (read-project (project-name-to-file name))
	src-deps (map #(parse % :project-dependency) (:src-dependencies project-data))
	jar-deps (map parse (:dependencies project-data))
	native-deps (map #(parse % :native-dependency) (:native-dependencies project-data))]
    (concat src-deps jar-deps native-deps)))

(defn extract-project-exclusions [name]
  (let [exclusion-data (-> name
			   project-name-to-file
			   read-project
			   :exclusions)]
    (map #(parse % :project-exclusion) exclusion-data)))

(defrecord project-dependency [name]
  ADependency
  (obtain [this _]
	  (new-file system-root "usr/src" name "src"))
  (depends-on [this]
	      (extract-project-dependencies name))
  (load-type [this] :src)
  (exclusions [this]
	      (extract-project-exclusions name)))

(defn pass-pom-data
  "grabs from cache if possible"
  [name f]
  (-> (new-file system-root "usr/src" name "pom.xml")
      clojure.xml/parse
      dj.deps.maven/condense-xml
      (f nil)))

(defrecord source-contrib-dependency [name]
  ADependency
  (obtain [this _]
	  (let [f (new-file system-root "usr/src" name "src/main/clojure")]
	    (when-not (.exists f)
	      (let [clojure-folder (new-file system-root "usr/src/clojure")]
		(when-not (.exists clojure-folder)
		  (mkdir clojure-folder)))
	      (sh "git" "clone" (str "git://github.com/" name ".git")
		  :dir (get-path (new-file system-root "usr/src/clojure"))))
	    f))
  (depends-on [this]
	      (pass-pom-data name dj.deps.maven/pom-extract-dependencies))
  (load-type [this] :src)
  (exclusions [this]
	      (pass-pom-data name dj.deps.maven/pom-extract-exclusions)))

(defrecord git-dependency [name git-path]
  ADependency
  (obtain [this _]
	  (let [f (new-file system-root "usr/src" name "src")]
	    (when-not (.exists f)
	      (sh "git" "clone" git-path
		  :dir (get-path (new-file system-root "usr/src/"))))
	    f))
  (depends-on [this]
	      (extract-project-dependencies name))
  (load-type [this] :src)
  (exclusions [this]
	      (extract-project-exclusions name)))

(defrecord custom-jar-dependency [name filename]
  ADependency
  (obtain [this _]
	  (let [f (new-file system-root "usr/src" name filename)]
	    (when-not (.exists f)
	      (throw (Exception. (str "file " (.getPath f) " not found"))))
	    f))
  (depends-on [this]
	      (extract-project-dependencies name))
  (load-type [this] :src)
  (exclusions [this]
	      (extract-project-exclusions name)))

(defmethod parse :project-exclusion [obj & [_]]
	   (let [id (first obj)
		 name (name id)
		 group (or (namespace id)
			   (clojure.core/name id))
		 version (second obj)
		 version-p (if (= version "*")
			     (fn [d] true)
			     (fn [d] (= (:version d) version)))]
	     (fn [d] (and (= (:group d) group)
			  (= (:name d) name)
			  (version-p d)))))

(defmethod parse :project-dependency [name & [_]]
	   (let [components (.split #"/" name)]
	     (if (re-find #"http://|git://|https://" name)
	      (let [[_ n] (re-find #"((?:\w|-|_)+)\.git" (last components))]
		(git-dependency. n name))
	      (if (= "clojure" (first components))
		(source-contrib-dependency. name)
		(let [foldername (apply str (interpose "/" (drop-last components)))
		      filename (last components)]
		  (if (and (not (empty? foldername))
			   (not (empty? name))
			   (.contains filename ".jar"))
		    (custom-jar-dependency. foldername filename)
		    (project-dependency. name)))))))
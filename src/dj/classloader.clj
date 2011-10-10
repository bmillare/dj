(ns dj.classloader
  (:import [java.net URISyntaxException])
  (:require [dj.toolkit :as tk])
  (:require [dj.core])
  (:require [dj.deps])
  (:require [dj.deps core project])
  (:require [clojure.set :as set]))

;; BUG
;; classlojure works by first having a reference to the root
;; classloader (.getParent (.getClassLoader clojure.lang.RT)) and then
;; making a new java.net.URLClassLoader and setting the threads
;; classloader with (.setContextClassLoader (Thread/currentThread) cl#)

;; for future, nailgun like dj, need to set parent of brand new
;; urlclassloader to be root so that separate clojure instances can be run
;; also need to use try and finally to restore classloader
;; or also do invoke in and in different thread style

(def +boot-classpaths+
     (apply conj #{} (map #(tk/new-file %)
			  (.split (System/getProperty "java.class.path")
				  (System/getProperty "path.separator")))))

(defn url-to-file [url]
  (try
   (java.io.File. (.toURI url))
   (catch URISyntaxException e
       (tk/new-file (.getPath url)))))

(defn get-classpaths [classloader]
  (let [files (map url-to-file (.getURLs classloader))]
    (if (empty? files)
      +boot-classpaths+
      (apply conj +boot-classpaths+ files))))

(defn get-current-classloader []
  (.getContextClassLoader (Thread/currentThread)))

(defn unchecked-add-to-classpath!
  "given file, a jar or a directory, adds it to classpath for classloader"
  [classloader f]
  (let [f (tk/new-file f)]
    (.addURL classloader (.toURL (.toURI f)))
    f))

(defn add-to-classpath!
  "given file, a jar or a directory, adds it to classpath for classloader

   ASSUMES a jars with the same path are identical"
  [classloader f]
  (let [f (tk/new-file f)]
   (when-not ((get-classpaths classloader) f)
     (.addURL classloader (.toURL (.toURI f)))
     f)))

(defn reload-class-file
  "Reload a .class file during runtime, this allows you to recompile
  java components, and reload their class files to get the updated
  class definitions"
  [path]
  (let [f (tk/new-file path)
	classname (second (re-matches #"(\w+)\.class" (.getName f)))]
    (.defineClass (clojure.lang.DynamicClassLoader.)
		  classname
		  (tk/to-byte-array f)
		  nil)))

(defn add-dependencies!
  "given a classloader, takes a list of strings or project.clj
  dependencies ie. [foo/bar \"1.2.2\"], options passed to
  obtain-dependencies!"
  ([cl ds options]
     (let [existing-paths (get-classpaths cl)
	   d-objs (for [d ds
			:let [r (cond
				 (vector? d) (dj.deps.core/parse d)
				 (string? d) (dj.deps.core/parse d :project-dependency)
				 (symbol? d) (dj.deps.core/parse d :project-dependency))]
			:when r]
		    r)
	   [src-paths jar-paths native-paths]
	   (dj.deps/obtain-dependencies! d-objs options)
	   src-jar-paths (set/difference (set/union (set src-paths) (set jar-paths)) existing-paths)]
       ;; Reset java.library.path by setting sys_paths variable in java.lang.ClassLoader to NULL, depends on java implementation knowledge
       (let [clazz java.lang.ClassLoader
	     field (.getDeclaredField clazz "sys_paths")] 
	 (.setAccessible field true)
	 (.set field clazz nil)
	 (System/setProperty "java.library.path" (apply str (interpose ";" native-paths))))
       (doseq [p src-jar-paths]
	 (dj.classloader/unchecked-add-to-classpath! cl p))))
  ([cl ds]
     (add-dependencies! cl ds {:verbose true :offline true})))
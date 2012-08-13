(ns dj.classloader
  (:import [java.net URISyntaxException])
  (:require [dj]
	    [dj.io :as io]
	    [clojure.set :as set]))

(def boot-classpaths
     (apply conj #{} (map #(io/file %)
			  (.split (System/getProperty "java.class.path")
				  (System/getProperty "path.separator")))))

(defn url-to-file [^java.net.URL url]
  (try
    (java.io.File. ^java.net.URI (.toURI url))
    (catch URISyntaxException e
      (io/file (.getPath url)))))

(defn get-classpaths [^java.net.URLClassLoader classloader]
  (let [files (map url-to-file (.getURLs classloader))]
    (if (empty? files)
      boot-classpaths
      (apply conj boot-classpaths files))))

(defn get-current-classloader ^ClassLoader []
  (.getContextClassLoader (Thread/currentThread)))

(defn unchecked-add-to-classpath!
  "adds file to classpath for classloader"
  [classloader ^java.io.File f]
  (let [clazz (class classloader)]
    (if (= clazz
	   sun.misc.Launcher$AppClassLoader)
      (let [method (.getDeclaredMethod clazz "appendToClassPathForInstrumentation" (into-array Class [java.lang.String]))] 
	(.setAccessible method true)
	(.invoke method classloader (into-array Object [(.getPath f)])))
      (.addURL ^java.net.URLClassLoader classloader
	       ^java.net.URL (.toURL ^java.net.URI (.toURI f)))))
  f)

(defn add-to-classpath!
  "given file, a jar or a directory, adds it to classpath for classloader

   ASSUMES a jars with the same path are identical"
  ([classloader path]
     (let [f (if (= (first path)
		    \/)
	       (io/file path)
	       (io/file dj/system-root path))]
       (when-not ((get-classpaths classloader) f)
	 (unchecked-add-to-classpath! classloader f))))
  ([path]
     (add-to-classpath! (.getParent (get-current-classloader))
			path)))

(defn reload-class-file
  "Reload a .class file during runtime, this allows you to recompile
  java components, and reload their class files to get the updated
  class definitions"
  [path]
  (let [f (io/file path)
	classname (second (re-matches #"(\w+)\.class" (.getName f)))]
    (.defineClass (clojure.lang.DynamicClassLoader.)
		  classname
		  (io/to-byte-array f)
		  nil)))

(defn reset-native-paths! [native-paths]
  ;; Reset java.library.path by setting sys_paths variable in
  ;; java.lang.ClassLoader to NULL, depends on java implementation
  ;; knowledge
  (let [clazz java.lang.ClassLoader
	field (.getDeclaredField clazz "sys_paths")] 
    (.setAccessible field true)
    (.set field clazz nil)
    (System/setProperty "java.library.path" (apply str (interpose ";" native-paths)))))

(defn resource-as-str [str-path]
  (let [is (.getResourceAsStream (get-current-classloader) str-path)]
    (apply str (map char (take-while #(not= % -1) (repeatedly #(.read is)))))))

(defn find-resource
  (^java.io.File [relative-path]
     (find-resource relative-path (.getParent (get-current-classloader))))
  (^java.io.File [relative-path ^ClassLoader classloader]
     (io/file
      (.getPath
       (.getResource classloader
		     relative-path)))))
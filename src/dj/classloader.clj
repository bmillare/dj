(ns dj.classloader
  (:import [java.net URISyntaxException])
  (:require [dj]
	    [cemerick.pomegranate :as pom]
	    [dj.io :as io]
	    [clojure.set :as set])
  (:refer-clojure :exclude (add-classpath)))

(dj/import-fn #'pom/add-classpath)
(dj/import-fn #'pom/get-classpath)
(dj/import-fn #'pom/classloader-hierarchy)

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
    (System/setProperty "java.library.path" (apply str (interpose (if (re-find #"(?i)windows"
									       (System/getProperty "os.name"))
								    ";"
								    ":")
								  native-paths)))))

(defn append-native-path! [new-paths]
  (let [previous-paths (clojure.string/split (System/getProperty "java.library.path")
					     #":|;")]
    (reset-native-paths! (concat previous-paths new-paths))))

(defn resource-as-str [str-path]
  (let [is (.getResourceAsStream (first (pom/classloader-hierarchy)) str-path)]
    (apply str (map char (take-while #(not= % -1) (repeatedly #(.read is)))))))

(defn find-resource
  (^java.io.File [relative-path]
     (find-resource relative-path (second (pom/classloader-hierarchy))))
  (^java.io.File [relative-path ^ClassLoader classloader]
     (io/file
      (.getPath
       (.getResource classloader
		     relative-path)))))
(ns dj.classloader
  (:import [java.io File])
  (:require [dj.repository :as repo]))

(defonce *latest-id* (ref 0))

(defn make-id! []
  (alter *latest-id* inc))

(defonce *classloaders* (ref {}))

(defonce *classpaths*
  (ref (apply conj #{} (map #(File. %)
			    (.split (System/getProperty "java.class.path")
				    (System/getProperty "path.separator"))))))

(defn get-classloader []
  (clojure.lang.RT/baseLoader))

(defn add-to-classpath!
  "given file, a jar or a directory, adds it to classpath

   ASSUMES a jars with the same path are identical"
  [#^File f]
  (when-not (@*classpaths* f)
    (.addURL (get-classloader) (.toURL (.toURI f)))
   (dosync (alter *classpaths* conj f))
   f))

(defn add-dependency-to-classpath!
  "given dependency, adds it to classpath"
  [dependency]
  (add-to-classpath! (repo/get-dependency-path dependency ".jar")))

(defn testo []
  (let [cl (get-classloader)]
    (add-dependency-to-classpath! ['swank-clojure/swank-clojure "1.1.0"])
    (doall (map prn (.getURLs cl)))
    (require 'swank.core)
    (clojure.main/main))
  nil)
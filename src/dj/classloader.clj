(ns dj.classloader
  (:require [dj.repository :as repo]))

(defn get-classloader []
  (clojure.lang.RT/baseLoader))

(defn add-to-classpath! [#^File f]
  "given file, a jar or a directory, adds it to classpath"
  (.addURL (get-classloader) (.toURL (.toURI f))))

(defn add-dependency-to-classpath! [dependency]
  "given dependency, adds it to classpath"
  (add-to-classpath! (repo/get-dependency-path dependency ".jar")))

(defn testo []
  (add-dependency-to-classpath! ['swank-clojure/swank-clojure "1.1.0"])
  (doall (map prn (.getURLs (get-classloader))))
  (require 'swank.core)
  
  nil)
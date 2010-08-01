(ns dj.classloader
  (:import [java.io File])
  (:import [java.net URISyntaxException])
  (:require [dj.repository :as repo])
  (:require [dj.core]))

(def +boot-classpaths+
     (apply conj #{} (map #(File. %)
			  (.split (System/getProperty "java.class.path")
				  (System/getProperty "path.separator")))))

(defn url-to-file [url]
  (try
   (File. (.toURI url))
   (catch URISyntaxException e
       (File. (.getPath url)))))

(defn get-classpaths [classloader]
  (let [files (map url-to-file (.getURLs classloader))]
    (if (empty? files)
      +boot-classpaths+
      (apply conj +boot-classpaths+ files))))

(defn get-current-classloader []
  (clojure.lang.RT/baseLoader))

(defn add-to-classpath!
  "given file, a jar or a directory, adds it to classpath for classloader

   ASSUMES a jars with the same path are identical"
  [classloader f]
  (let [f (java.io.File. f)]
   (when-not ((get-classpaths classloader) f)
     (.addURL classloader (.toURL (.toURI f)))
     f)))

(defn with-new-classloader-helper
  [caller-ns
   src-paths
   jar-paths
   native-paths
   body]
  `(let [cl# (dj.classloader/get-current-classloader)]
     (in-ns '~caller-ns)
     (def ~'*classloader* cl#)
     ;; Reset java.library.path by setting sys_paths variable in java.lang.ClassLoader to NULL, depends on java implementation knowledge
     (doall (for [p# ~native-paths]
	      (let [clazz# java.lang.ClassLoader
		    field# (.getDeclaredField clazz# "sys_paths")] 
		(.setAccessible field# true)
		(.set field# clazz# nil)
		(System/setProperty "java.library.path" p#))))
     (doall (for [p# ~src-paths]
	      (dj.classloader/add-to-classpath! cl# p#)))
     (doall (for [p# ~jar-paths]
	      (dj.classloader/add-to-classpath! cl# p#)))
     ~@body))

;; (java.io.File. dj.core/system-root (str "usr/src/" ~project-name "/src"))
;; (defn add-dependency-to-classpath!
;;   "given dependency, adds it to classpath for current classloader"
;;   [classloader dependency]
;;   (add-to-classpath! classloader (repo/get-dependency-path dependency ".jar")))

(defmacro with-new-classloader [src-paths
				jar-paths
				native-paths
				& body]
  "running in an eval implicitly creates a new classloader
   assume empty environment"
  (let [caller-ns (symbol (ns-name *ns*))]
    `(eval (dj.classloader/with-new-classloader-helper
	     '~caller-ns
	     ~src-paths
	     ~jar-paths
	     ~native-paths
	     '~body))))
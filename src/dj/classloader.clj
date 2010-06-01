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
  [classloader #^File f]
  (when-not ((get-classpaths classloader) f)
    (.addURL classloader (.toURL (.toURI f)))
    f))

(defn add-dependency-to-classpath!
  "given dependency, adds it to classpath for current classloader"
  [classloader dependency]
  (add-to-classpath! classloader (repo/get-dependency-path dependency ".jar")))

(defn with-new-classloader-helper
  [caller-ns project-name dependencies body]
  `(let [cl# (dj.classloader/get-current-classloader)]
     (in-ns '~caller-ns)
     (def ~'*classloader* cl#)
     (doall (map (fn [~'d] (dj.classloader/add-dependency-to-classpath!
			    cl#
			    ~'d))
		 '~dependencies))
     (dj.classloader/add-to-classpath!
      cl#
      (java.io.File. dj.core/system-root (str "usr/src/" ~project-name "/src")))
     ~@body))

(defmacro with-new-classloader [project-name dependencies & body]
  "running in an eval implicitly creates a new classloader
   assume empty environment"
  (let [caller-ns (symbol (ns-name *ns*))]
    `(eval (dj.classloader/with-new-classloader-helper '~caller-ns ~project-name ~dependencies '~body))))

(defn testo []
  nil)
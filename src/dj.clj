(ns dj
  (:require [dj.toolkit :as tk])
  (:require [dj.toolkit.experimental.meta :as djmeta])
  (:require [dj.classloader :as cl])
  (:require [dj.deps core project maven])
  (:require [dj.core]))

(tk/import-fn #'cl/get-classpaths)
(tk/import-fn #'cl/get-current-classloader)
(tk/import-fn #'cl/reload-class-file)
(tk/import-fn #'cl/reset-native-paths!)
(tk/import-fn #'dj.deps.maven/ls-repo)

(def repository-urls dj.deps.maven/repository-urls)
(def system-root (java.io.File. (System/getProperty "user.dir")))

(defn add-repository! [url-str]
  (swap! repository-urls
	 (fn [coll v]
	   (if (some #{v} coll)
	     coll
	     (conj coll (dj.deps.maven/validate-repository-url v))))
	 url-str))

(defn add-to-classpath!
  "given file, a jar or a directory, adds it to classpath for classloader

   ASSUMES a jars with the same path are identical"
  ([classloader path]
     (let [f (tk/new-file path)]
       (when-not ((cl/get-classpaths classloader) f)
	 (dj.classloader/unchecked-add-to-classpath! classloader f))))
  ([path]
     (add-to-classpath! (.getParent (cl/get-current-classloader))
			path)))

(defn add-dependencies!
  "given a classloader (default is parent classloader), takes a list
  of strings or project.clj dependencies ie. [foo/bar \"1.2.2\"],
  options passed to obtain-dependencies!"
  ([dependencies]
     (add-dependencies! (.getParent (cl/get-current-classloader))
			dependencies
			{:verbose true :offline true}))
  ([classloader dependencies options]
     (let [d-objs (for [d dependencies
			:let [r (cond
				 (vector? d) (dj.deps.core/parse d)
				 (string? d) (dj.deps.core/parse d :project-dependency)
				 (symbol? d) (dj.deps.core/parse d :project-dependency))]
			:when r]
		    r)]
       (cl/add-dependencies! classloader d-objs options))))

(defn add-native!
  "given a classloader (default is parent classloader), takes a list
  of strings or project.clj native dependencies ie. [foo/bar
  \"1.2.2\"], options passed to obtain-dependencies!"
  ([dependencies]
     (add-native! (.getParent (cl/get-current-classloader))
			dependencies
			{:verbose true :offline true}))
  ([classloader dependencies options]
     (cl/add-dependencies! classloader
			   (for [d dependencies
				 :let [r (dj.deps.core/parse d :native-dependency)]
				 :when r]
			     r)
			   options)))

(defn add-cljs-to-classpath! []
  (let [cljs-dir (tk/new-file dj.core/system-root "usr/src/clojurescript")
	paths (concat (filter #(not= % (tk/new-file cljs-dir "lib/clojure.jar"))
			      (tk/ls (tk/new-file cljs-dir "lib")))
		      (map #(tk/new-file cljs-dir %)
			   ["src/clj"
			    "src/cljs"
			    "test/cljs"]))]
    (doseq [p paths]
      (add-to-classpath! p))))

(defn obj-seq-print* [s type-name obj-str-fn]
  (println (str type-name ": " (count s) " found"))
  (doseq [x s]
    (println (obj-str-fn x))))

(defn usr-search
  "search local repositories for files matching query"
  [query]
  (obj-seq-print* (djmeta/toog* query
				(djmeta/files-in-folders [(tk/new-file dj.core/system-root "usr")])
				#(.getPath %))
		  "Files"
		  #(.getPath %)))

(defn local-jar-versions
  "search local maven repositories for jars matching query"
  [query]
  (obj-seq-print* (djmeta/toog* (str query " jar")
				(djmeta/files-in-folders [(tk/new-file dj.core/system-root "usr/maven")
							  (tk/new-file dj.core/system-root "usr/native")])
				#(.getName %))
		  "jars"
		  #(.getName %)))

(defn resource-as-str [str-path]
  (let [is (.getResourceAsStream (get-current-classloader) str-path)]
    (apply str (map char (take-while #(not= % -1) (repeatedly #(.read is)))))))

(defn find-resource
  (^java.io.File [relative-path]
     (find-resource relative-path (.getParent (get-current-classloader))))
  (^java.io.File [relative-path ^ClassLoader classloader]
     (tk/new-file
      (.getPath
       (.getResource classloader
		     relative-path)))))
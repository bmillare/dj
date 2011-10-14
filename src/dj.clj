(ns dj
  (:require [dj.toolkit :as tk])
  (:require [dj.classloader :as cl])
  (:require [dj.deps core project maven]))

(tk/import-fn #'cl/get-classpaths)
(tk/import-fn #'cl/get-current-classloader)
(tk/import-fn #'cl/reload-class-file)
(tk/import-fn #'dj.deps.maven/ls-repo)

(defn get-repository-urls []
  dj.deps.maven/repository-urls)

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
(ns dj.cli.test
  "run clojure tests from command line"
  (:require [dj.classloader]
	    [dj.cli]
	    [dj.deps.project]
	    [dj.deps]
	    [dj.core]
	    [clojure.test]
	    [dj.toolkit :as tk]))

(defn main
  "USAGE: dj test <project-name> <namespaces>..."
  [project-name & namespaces]
  (if (and project-name namespaces)
    (let [default-options {:verbose true
			   :offline true}
	  options default-options
	  cl (clojure.lang.RT/baseLoader)]
      (.setContextClassLoader (Thread/currentThread) cl)
      (dj.classloader/add-to-classpath! cl (.getCanonicalPath (tk/new-file dj.core/system-root "usr/src" project-name "test")))
      (dj.classloader/add-dependencies! cl
					[project-name]
					options)
      (require 'clojure.test)
      (doseq [n namespaces]
	(require [(symbol n)]))
      (doseq [n namespaces]
	(clojure.test/run-tests (symbol n)))
      (shutdown-agents))
    (println "USAGE: dj test <project-name> <namespaces>...")))
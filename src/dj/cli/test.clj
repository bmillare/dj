(ns dj.cli.test
  "run clojure tests from command line"
  (:use [dj.toolkit :only [new-file]])
  (:require [dj.classloader]
	    [dj.cli]
	    [dj.deps.project]
	    [dj.deps]
	    [dj.core]
	    [clojure.test]))

(defn main
  "USAGE: dj test <project-name> <namespaces>..."
  [project-name & namespaces]
  (if (and project-name namespaces)
    (let [requires (map (fn [n]
			  `(require '~(symbol n)))
			namespaces)
	  run-tests (map (fn [n]
			  `(clojure.test/run-tests '~(symbol n)))
			namespaces)
	  
	  default-options {:verbose true
			   :offline true}
	  options default-options
	  [src-paths jar-paths native-paths] (dj.deps/obtain-dependencies!
					      [(dj.deps.project.project-dependency. project-name)]
					      options)
	  src-paths (conj src-paths (dj.io/string (new-file dj.core/system-root "usr/src" project-name "test")))]
      (println src-paths)
      (dj.classloader/with-new-classloader
	src-paths
	jar-paths
	native-paths
	`(do (require 'clojure.test)
	     ~@requires
	     ~@run-tests)))
    (println "USAGE: dj test <project-name> <namespaces>...")))
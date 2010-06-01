(ns dj.cli.repl
  "start a repl"
  (:require [dj.deps])
  (:require [dj.classloader :as classloader])
  (:require [dj.cli :as cli]))

(defn main
  "dj repl [project-name]
   if no project-name given, starts a generic repl

   if project-name given, starts repl with classpath set so
   dependencies can be fulfilled, ie. you should have access to the
   projects sources and jar dependencies"
  [& args]
  (if-let [project-name (first args)]
    (let [get-deps (fn [p]
		     (dj.deps/get-all-dependencies! (:dependencies p)
						    (:exclusions p)))
	  dependencies (->
			project-name
			cli/project-name-to-file
			cli/read-project
			get-deps)]
      (dj.classloader/with-new-classloader
	project-name
	dependencies
	(clojure.main/main)))
    (clojure.main/main)))


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
    (let [project-data (->
			project-name
			cli/project-name-to-file
			cli/read-project)]
      (dj.classloader/with-new-classloader
	project-name
	(dj.deps/get-all-dependencies! (:dependencies project-data)
				       {:hooks [(dj.deps/resolved-hook nil dj.deps/exclude-id)
						(dj.deps/exclude-hook (:exclusions project-data) dj.deps/exclude-id)]})
	(clojure.main/main)))
    (clojure.main/main)))


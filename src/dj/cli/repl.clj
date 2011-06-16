(ns dj.cli.repl
  "start a repl"
  (:require [dj.classloader])
  (:require [dj.cli])
  (:require [dj.deps.project])
  (:require [dj.deps])
  (:require [dj.core]))

(defn main
  "dj repl [project-name]
   if no project-name given, starts a generic repl

   if project-name given, starts repl with classpath set so
   dependencies can be fulfilled, ie. you should have access to the
   projects sources and jar dependencies"
  [& args]
  (if-let [project-name (first args)]
    (let [args (map read-string (next args))
	  default-options {:verbose true
			   :offline true}
	  options (if (empty? args)
		    default-options
		    (apply assoc default-options args))
	  [src-paths jar-paths native-paths] (dj.deps/obtain-dependencies! [(dj.deps.project.project-dependency. project-name)] options)]
      (dj.classloader/with-new-classloader
	src-paths
	jar-paths
	native-paths
	'(clojure.main/main)))
    (clojure.main/main)))


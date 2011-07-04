(ns dj.cli.run
  "run clojure script from command line"
  (:require [dj.classloader])
  (:require [dj.cli])
  (:require [dj.deps.project])
  (:require [dj.deps])
  (:require [dj.core]))

(defn main
  "USAGE: dj run <filename> [project-name]"
  [filename & args]
  (if-let [project-name (first args)]
    (let [args (for [a (next args)]
		 (if (= a "false")
		   false
		   (keyword a)))
	  default-options {:verbose true
			   :offline true}
	  options (if (empty? args)
		    default-options
		    (apply assoc default-options args))
	  [src-paths
	   jar-paths
	   native-paths] (dj.deps/obtain-dependencies!
			  [(dj.deps.project.project-dependency. project-name)]
			  options)]
      (dj.classloader/with-new-classloader
	src-paths
	jar-paths
	native-paths
	`(load-file ~filename)))
    (load-file filename)))
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
  (let [project-name (first args)
	args (map read-string (next args))
	default-options {:verbose true
			 :offline true}
	options (if (empty? args)
		  default-options
		  (apply assoc default-options args))
	cl (dj.cli/use-baseloader!)]
    (dj.classloader/add-dependencies! cl
				      (if project-name
					[(dj.deps.project/->project-dependency project-name)]
					nil)
				      options)
    (load-file filename)))
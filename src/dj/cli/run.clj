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
	cl (clojure.lang.RT/baseLoader)]
    (.setContextClassLoader (Thread/currentThread) cl)
    (dj.classloader/add-dependencies! cl
				      (if project-name
					[project-name]
					nil)
				      options)
    (load-file filename)))
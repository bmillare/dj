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
    (clojure.main/repl
     :init (fn []
	     (println "Clojure" (clojure-version))
	     (in-ns 'user))
     :prompt (fn [] (printf ";%s=> " (ns-name *ns*))))))


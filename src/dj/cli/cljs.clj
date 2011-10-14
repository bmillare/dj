(ns dj.cli.cljs
  "clojurescript utilities"
  (:require [dj.toolkit :as tk])
  (:require [dj.classloader])
  (:require [dj.cli])
  (:require [dj.core])
  (:require [dj.deps.project])
  (:require [clojure.java.shell :as sh]))

(defn main
  "dj cljs <install|repl|update> [options]
   install - installs and bootstraps clojurescript
   repl - starts clojure repl with clojurescript in classpath
   update - updates and re-bootstraps clojurescript"
  [& args]
  (let [command (first args)
	args (map read-string (next args))
	default-options {:verbose true
			 :offline true}
	options (if (empty? args)
		  default-options
		  (apply assoc default-options args))
	cl (dj.cli/use-baseloader!)
	src-dir (tk/get-path (tk/new-file dj.core/system-root "usr/src/"))
	cljs-dir (tk/str-path src-dir "clojurescript")]
    (case command
	  "repl" (let []
		   (doseq [paths (concat (filter #(not= % (tk/new-file cljs-dir "lib/clojure.jar"))
						 (tk/ls (tk/new-file cljs-dir "lib")))
					 (map #(tk/new-file cljs-dir %)
					      ["src/clj"
					       "src/cljs"
					       "test/cljs"]))]
		     (dj.classloader/unchecked-add-to-classpath! cl paths))
		   (clojure.main/repl
		    :init (fn []
			    (println "Clojure" (clojure-version) "with Clojurescript")
			    (in-ns 'user))
		    :prompt (fn [] (printf ";%s=> " (ns-name *ns*)))))
	  "install" (let []
		      (sh/sh "git" "clone" "git://github.com/clojure/clojurescript.git"
			     :dir src-dir)
		      (sh/sh "script/bootstrap"
			     :dir cljs-dir))
	  "update" (let []
		     (sh/sh "git" "pull" 
			    :dir cljs-dir)
		     (sh/sh "scripts/bootstrap"
			    :dir cljs-dir))
	  (println "dj cljs <install|repl|update> [options]
   install - installs and bootstraps clojurescript
   repl - starts clojure repl with clojurescript in classpath
   update - updates and re-bootstraps clojurescript"))
    (shutdown-agents)))


(ns dj.cli.rlwrap
  "rlwrap (enhanced repl) utilities"
  (:require [dj.core])
  (:require [dj.toolkit :as tk]))

(defn main
  "dj rlwrap [command]
   install - install rlwrap (todo, depends on os)
   make-completions - generate completions file for core"
  [& [command]]
  (case command
	"install" (println "todo, depends on os, for ubuntu type $ sudo apt-get install rlwrap")
	"make-completions"
	(with-open [f (java.io.BufferedWriter. (java.io.FileWriter. (let [comp-f (tk/new-file dj.core/system-root "etc/rlwrap-clj-completions")
									  etc-f (.parent comp-f)]
								      (when-not (.exists etc-f) (tk/mkdir etc-f))
								      comp-f)))]
	  (.write f (apply str (interleave (keys (ns-publics (find-ns 'clojure.core))) (repeat "\n")))))

	(println "dj rlwrap [command]
   install - install rlwrap (todo, depends on os)
   make-completions - generate completions file for core")))
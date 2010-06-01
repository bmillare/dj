(ns dj.cli.rlwrap
  "rlwrap (enhanced repl) utilities"
  (:require [dj.core]))

(defn main
  "dj rlwrap [command]
   install - install rlwrap (todo, depends on os)
   make-completions - generate completions file for core"
  [& [command]]
  (case command
	"install" (println "todo, depends on os")
	"make-completions"
	(with-open [f (java.io.BufferedWriter. (java.io.FileWriter. (java.io.File. dj.core/system-root "etc/rlwrap-clj-completions")))]
	  (.write f (apply str (interleave (keys (ns-publics (find-ns 'clojure.core))) (repeat "\n")))))

	(println "command:" command "not found")))
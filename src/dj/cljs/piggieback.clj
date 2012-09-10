(ns dj.cljs.piggieback
  (:require [cemerick.piggieback]
	    [cljs.repl]
	    [cljs.repl.browser]))

(defn cljs-repl [{:keys [port working-dir]}]
  (cemerick.piggieback/cljs-repl
   :repl-env (doto (cljs.repl.browser/repl-env :port port :working-dir working-dir)
	       cljs.repl/-setup)))
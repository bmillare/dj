(ns dj.cljs.piggieback
  (:require [cemerick.piggieback]
	    [cljs.repl]
	    [cljs.repl.browser]))

(defn cljs-repl
  "port: for repl/server
   working-dir: path/file to generated js
   To connect to server, run cljs in browser (clojure.browser.repl/connect \"http://localhost:<port>/repl\")
   Make sure advanced optimizations is not activated. Simple works though.

   Use load-file or load-namespace to do dynamic development"
  [{:keys [port working-dir]}]
  (cemerick.piggieback/cljs-repl
   :repl-env (doto (cljs.repl.browser/repl-env :port port :working-dir working-dir)
	       cljs.repl/-setup)))
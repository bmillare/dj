(defproject dj "2.0.0"
  :description "A library of utilities"
  :dependencies [[org.clojure/clojure "1.4.0"]
		 [org.eclipse.jgit/org.eclipse.jgit "2.0.0.201206130900-r"]
		 [leiningen-core "2.0.0-SNAPSHOT"]
		 [com.cemerick/piggieback "0.0.2"]]
  :injections [(require 'cemerick.piggieback)]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})
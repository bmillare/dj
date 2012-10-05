(defproject dj "2.0.0"
  :description "A library of utilities"
  :dependencies [[org.clojure/clojure "1.4.0"]
		 [org.eclipse.jgit/org.eclipse.jgit "2.0.0.201206130900-r"]
		 [leiningen-core "2.0.0-preview10"]
		 [com.cemerick/piggieback "0.0.2"]
		 [org.apache.directory.studio/org.apache.commons.io "2.1"]
                 [org.clojure/tools.namespace "0.2.0"]]
  :injections [(require '[cemerick.piggieback]
			'[dj.repl]
			'[dj.dependencies]
			'[dj.cljs]
                        '[clojure.tools.namespace.repl])]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})
(defproject dj "2.0.0"
  :description "A library of utilities"
  :dependencies [[org.clojure/clojure "1.4.0"]
		 [org.eclipse.jgit/org.eclipse.jgit "2.0.0.201206130900-r"]
		 [leiningen-core "2.0.0-preview10"]
		 [org.apache.directory.studio/org.apache.commons.io "2.1"]
                 [org.clojure/tools.namespace "0.2.0"]]
  :injections [(require '[dj.repl]
			'[dj.dependencies]
			'[clojure.tools.namespace.repl])]
  :profiles {:database {:dependencies [[com.datomic/datomic-free "0.8.3546"]]}})
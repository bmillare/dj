(defproject dj "2.0.1"
  :description "A library of utilities"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.eclipse.jgit/org.eclipse.jgit "2.0.0.201206130900-r"]
		 [leiningen-core "2.1.3"]
		 [org.apache.directory.studio/org.apache.commons.io "2.1"]
                 [org.clojure/tools.namespace "0.2.4"]]
  :injections [(require '[dj.repl]
			'[dj.dependencies]
			'[clojure.tools.namespace.repl])]
  :profiles {:database {:dependencies [[com.datomic/datomic-free "0.8.3789"]]}
             :cljs {:dependencies [[org.clojure/google-closure-library "0.0-2029"]
                                   [org.clojure/google-closure-library-third-party "0.0-2029"]
                                   [org.clojure/tools.reader "0.7.5"]
                                   [org.clojure/data.json "0.2.2"]]}}
  :jvm-opts ["-XX:MaxPermSize=256M"])

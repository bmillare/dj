(ns dj.cljs
  (:require [dj]
	    [dj.git]
	    [dj.io]
	    [dj.classloader]
	    [clojure.java.shell :as sh]
	    [cemerick.piggieback]
	    [cljs.analyzer]
	    [cljs.repl]
	    [cljs.repl.browser]))

(defn add-cljs-to-classpath!
  ([cljs-dir]
     (let [cljs-dir (dj.io/file cljs-dir)
	   paths (concat (filter #(not= % (dj.io/file cljs-dir "lib/clojure.jar"))
				 (dj.io/ls (dj.io/file cljs-dir "lib")))
			 (map #(dj.io/file cljs-dir %)
			      ["src/clj"
			       "src/cljs"
			       "test/cljs"]))]
       (doseq [p paths]
	 (dj.classloader/add-classpath (.getPath ^java.io.File p)))))
  ([]
     (add-cljs-to-classpath! (dj.io/file dj/system-root "usr/src/clojurescript"))))

(defn install-cljs! []
  (let [cljs-dir (dj.io/file dj/system-root "usr/src")]
    (when-not (dj.io/exists? (dj.io/file cljs-dir "clojurescript"))
      (dj.git/clone "git://github.com/clojure/clojurescript.git"
		    cljs-dir)
      (sh/sh "script/bootstrap"
	     :dir (dj.io/file cljs-dir "clojurescript")))))

(def cljs-repl-envs (atom []))

(defn cljs-repl
  "port: for repl/server
   working-dir: path/file to generated js
   To connect to server, run cljs in browser (clojure.browser.repl/connect \"http://localhost:<port>/repl\")
   Make sure advanced optimizations is not activated. Simple works though.

   Use load-file or load-namespace to do dynamic development"
  [{:keys [port working-dir]}]
  (let [repl-env (doto (cljs.repl.browser/repl-env :port port :working-dir working-dir)
		   cljs.repl/-setup)]
    (swap! cljs-repl-envs conj repl-env)
    (cemerick.piggieback/cljs-repl
     :repl-env repl-env)))

(defn cljs-eval
  ([repl-env env filename form]
     (cljs.repl/evaluate-form repl-env env filename form))
  ([repl-env form]
     (cljs.repl/evaluate-form repl-env
			      {:context :statement
			       :locals {}
			       :ns (cljs.analyzer/get-namespace 'cljs.user)}
			      "<dj.cljs/cljs-eval>" form))
  ([form]
     (let [repl-env (first @cljs-repl-envs)]
       (if repl-env
	 (cljs-eval repl-env form)
	 (throw (Exception. "No existing repl-env found in dj.cljs/cljs-repl-envs"))))))
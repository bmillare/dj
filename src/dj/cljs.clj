(ns dj.cljs
  (:require [dj]
	    [dj.git]
	    [dj.io]
            [dj.cljs]
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

(defn ->cljs-browser-env
  "port: for repl/server
working-dir: path/file to generated js

Creates a browser connected evaluation environment object and returns
it. This object wraps a repl-env

To connect to server, run cljs in browser
 (clojure.browser.repl/connect \"http://localhost:<port>/repl\")
Make sure advanced optimizations is not activated. Simple works though.

Use load-file or load-namespace to do dynamic development"
  [opts]
  (let [{:keys [port working-dir]} opts
        repl-env (cljs.repl.browser/repl-env :port port :working-dir working-dir)]
    (reify
      dj.repl/Lifecycle
      (start [this]
        (doto repl-env
          cljs.repl/-setup))
      (stop [this]
        (cljs.repl/-tear-down repl-env))
      clojure.lang.IDeref
      (deref [this]
        repl-env))))

(defn cljs-repl
  "delegates to cemerick.piggieback"
  [cljs-browser-env]
  (cemerick.piggieback/cljs-repl
   :repl-env @cljs-browser-env))

(defmacro capture-out-err [& body]
  `(let [o# (java.io.StringWriter.)
         e# (java.io.StringWriter.)]
     (binding [*out* o#
               *err* e#]
       (let [r# ~@body]
         {:return r#
          :out (str o#)
          :error (str e#)}))))

(defn cljs-eval
  "note this accepts the object returned from ->cljs-browser-env, not a repl-env"
  [cljs-browser-env form]
  (if (= form :cljs/quit)
    {:return :cljs/quit
     :out ""
     :error ""}
    (capture-out-err
     (cemerick.piggieback/cljs-eval @cljs-browser-env
                                    form
                                    nil))))

(defn load-file [cljs-browser-env f]
  (cljs.repl/load-file @cljs-browser-env f))

(defn load-namespace [cljs-browser-env n]
  (cljs.repl/load-namespace @cljs-browser-env n))
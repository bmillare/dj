(ns dj.cljs
  (:require [dj]
	    [dj.cljs.piggieback]
	    [dj.git]
	    [dj.io]
	    [dj.classloader]
	    [clojure.java.shell :as sh]))

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

(defn resolve-repl-dependencies []
  (add-cljs-to-classpath!)
  (load "dj/cljs/piggieback"))

(defn install-cljs! []
  (let [cljs-dir (dj.io/file dj/system-root "usr/src")]
    (when-not (dj.io/exists? (dj.io/file cljs-dir "clojurescript"))
      (dj.git/clone "git://github.com/clojure/clojurescript.git"
		    cljs-dir)
      (sh/sh "script/bootstrap"
	     :dir (dj.io/file cljs-dir "clojurescript")))))

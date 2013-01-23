(ns dj.cljs.install
  (:require [dj]
	    [dj.git]
	    [dj.io]
            [dj.classloader]
            [dj.dependencies]
	    [clojure.java.shell :as sh]))

(defn add-cljs-to-classpath!
  ([cljs-dir]
     (let [cljs-dir (dj.io/file cljs-dir)
	   paths (concat (filter #(and (not= % (dj.io/file cljs-dir "lib/clojure.jar"))
                                       (not= % (dj.io/file cljs-dir "lib/goog.jar")))
				 (dj.io/ls (dj.io/file cljs-dir "lib")))
			 (map #(dj.io/file cljs-dir %)
			      ["src/clj"
			       "src/cljs"
			       "test/cljs"
                               "closure/library/third_party/closure"]))]
       (doseq [p paths]
         (dj.classloader/add-classpath (.getPath ^java.io.File p)))
       ;; dynamic loading of these jars doesn't work for some reason
       #_ (dj.dependencies/add-dependencies :coordinates '[[org.clojure/google-closure-library "0.0-2029"]
                                                           [org.clojure/google-closure-library-third-party "0.0-2029"]])))
  ([]
     (add-cljs-to-classpath! (dj.io/file dj/system-root "usr/src/clojurescript"))))

(defn install-unix-git-cljs! []
  (let [cljs-dir (dj.io/file dj/system-root "usr/src")]
    (when-not (dj.io/exists? (dj.io/file cljs-dir "clojurescript"))
      (dj.git/clone "git://github.com/clojure/clojurescript.git"
		    cljs-dir)
      (sh/sh "script/bootstrap"
	     :dir (dj.io/file cljs-dir "clojurescript")))))

(defn install-snapshot-cljs! []
  nil)
(ns dj.cli.update
  "update dj, clojure version, or projects"
  (:require [dj.core])
  (:use [dj.net :only [wget!]])
  (:use [dj.toolkit :only [rm new-file str-path]])
  (:use [clojure.java.shell :only [sh]]))

;; only supports unix and require unzip to be installed
(defn main
  "USAGE: dj update [<project-name>|clojure|dj]"
  [& [opt]]
  (let [dj-dir (System/getProperty "user.dir")
	opt-dir (str-path dj-dir "opt")
	dispatch {"clojure" (fn []
			      (let [clojure-version "1.4.0-alpha5"
				    clojure-prefix (str "clojure-" clojure-version)
				    latest-url (str "http://repo1.maven.org/maven2/org/clojure/clojure/" clojure-version "/" clojure-prefix ".zip")
				    clojure-sym (str-path dj-dir "lib" "clojure.jar")]
				(println "Downloading newest version of clojure...")
				(wget! latest-url opt-dir)
				(println "Unzipping clojure archive...")
				(sh "unzip" (str clojure-prefix ".zip") :dir opt-dir)
				(println "Deleting clojure archive...")
				(rm (new-file opt-dir (str clojure-prefix ".zip")))
				(println "Switching links...")
				(rm (new-file clojure-sym))
				(sh "ln" "-s" (str-path opt-dir clojure-prefix (str clojure-prefix ".jar")) clojure-sym)
				(println "Done updating.")))
		  "dj" (fn [] (let [o (sh "git" "pull" :dir opt-dir)]
				(print (:out o))
				(print (:err o))))
		  nil (fn [] (println "USAGE: dj update [<project-name>|clojure|dj]"))}]
    (if-let [f (dispatch opt)]
      (f)
      (let [project-folder-path (str-path dj-dir "usr/src" opt)]
	(try
	 (let [o (sh "git" "pull" :dir project-folder-path)]
	   (print (:out o))
	   (print (:err o)))
	 (catch java.io.IOException e
	   (println "File" project-folder-path "not found")))))
    (shutdown-agents)))
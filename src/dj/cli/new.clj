(ns dj.cli.new
  "make a new project directory in dj/usr/src"
  (:require [dj.core :as dj])
  (:import [java.io File]))

(defn main
  "USAGE: dj new project-name"
  [& [project-name]]
  (let [dir (File. dj/system-root (str "usr/src/" project-name))]
    (if (.exists dir)
      (println (str "Directory " project-name " exists"))
      (do
	(.mkdir dir)
	(.mkdir (File. dir "src"))
	(.mkdir (File. dir (str "src/" project-name)))
	(spit (File. dir "project.clj")
	      (prn-str `(~'defproject ~(symbol project-name) "0.1.0"
			  :dependencies [[foo.bar/baz "1.0.0"]])))))))


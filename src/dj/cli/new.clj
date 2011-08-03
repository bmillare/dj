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
	      (str "(defproject " project-name " \"0.1.0\"
  :dependencies nil #_ [[foo.bar/baz \"1.0.0\"] [foo.baz/bar \"[1.2.0,)\"]]
  :src-dependencies nil #_ [\"incanter\" \"clojure/core.logic\" \"git://github.com/cgrand/enlive.git\"]
  :native-dependencies nil #_ [[penumbra/lwjgl \"2.4.2\"]])"))))))


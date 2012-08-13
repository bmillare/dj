(ns dj.dependencies
  (:require [cemerick.pomegranate :as pom]
	    [dj]
	    [dj.io]))

(defn extract-project-data [path]
  (let [[_ project-name version & rest] (read-string (slurp path))]
    (assoc (apply hash-map rest)
      :name (str project-name)
      :version version)))

(defn add-dependencies! [dependency-objects]
  (pom/add-dependencies :coordinates dependency-objects))

(defn resolve-project [relative-path]
  (let [project-dir (dj.io/file dj/system-root "usr/src" relative-path)]
    (-> (dj.io/file project-dir "project.clj")
	extract-project-data
	:dependencies
	add-dependencies!)))

;; need to add source dependencies, like that use git, and somehow make this recursive
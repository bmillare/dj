(ns dj.deps.project
  (:require [dj.io])
  (:require [dj.cli])
  (:use [dj.deps.core])
  (:use [dj.core :only [system-root]]))

(defrecord project-dependency [name])

(defmethod parse :project-exclusion [obj & [_]]
	   (let [id (first obj)
		 name (name id)
		 group (or (namespace id)
				    (name id))
		 version (second obj)]
	     (fn [d] (and (= (:group d) group)
			  (= (:name d) name)
			  (= (:version d) version)))))

(defmethod parse :project-dependency [obj & [_]]
	   (project-dependency. (name obj)))

(extend project-dependency
  ADependency
  {:obtain (fn [d]
	     (dj.io/file system-root (str "usr/src/" (:name d) "/src")))
   :depends-on (fn [d]
		 (let [project-data (-> (:name d)
					dj.cli/project-name-to-file
					dj.cli/read-project)
		       src-deps (map #(parse % :project-dependency) (:src-dependencies project-data))
		       jar-deps (map parse (:dependencies project-data))
		       native-deps (map #(parse % :native-dependency) (:native-dependencies project-data))]
		   (concat src-deps jar-deps native-deps)))
   :load-type (fn [d] :src)
   :exclusions (fn [d]
		 (let [exclusion-data (-> (:name d)
					  dj.cli/project-name-to-file
					  dj.cli/read-project
					  :exclusions)]
		   (map #(parse % :project-exclusion) exclusion-data)))})
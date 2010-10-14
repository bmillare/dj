(ns dj.core
  (:import [java.io File]))

(def system-root (File. (System/getProperty "user.dir")))

(defmacro defproject [project-name version & args]
  `(do
     (let [m# (apply hash-map (quote ~args))]
       (assoc m#
	 :name ~(name project-name)
	 :version ~version))))
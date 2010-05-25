(ns dj.cli
  (:import [java.io File FileNotFoundException])
  (:use [dj.core :only [defproject system-root]]))

(defn resolve-task [task]
  (let [task-ns (symbol (str "dj.cli." task))
        task-s 'main
        error-fn (fn [& _]
                   (println (str task " is not a task. Use \"help\" to list all tasks."))
		   (System/exit 1))]
    (try
     (require task-ns)
     (or (ns-resolve task-ns task-s)
         error-fn)
     (catch java.io.FileNotFoundException e
       error-fn))))

(defn read-project
  "in directory, looks for file project.clj and returns the result of
  running that file in the context of the dj.cli namespace"
  [#^File directory]
  (let [old-ns (ns-name *ns*)]
    (in-ns 'dj.core)
    (let [p (load-file (.getCanonicalPath (File. directory "project.clj")))]
      (in-ns old-ns)
      p)))

(defn main [& [task & args]]
  (let [task (or task "help")]
    (apply (resolve-task task) args)))
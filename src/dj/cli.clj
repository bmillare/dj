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

(defn main [& [task & args]]
  (let [task (or task "help")]
    (apply (resolve-task task) args)))
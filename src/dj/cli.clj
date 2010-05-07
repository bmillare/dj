(ns dj.cli
  (:import [java.io FileNotFoundException]))

(defn abort [msg]
  (println msg)
  (System/exit 1))

(defn resolve-task [task]
  (let [task-ns (symbol (str "dj." task))
        task (symbol (str "cli-" task))
        error-fn (fn [& _]
                   (abort
                    (format "%s is not a task. Use \"help\" to list all tasks."
                             task)))]
    (try
     (require task-ns)
     (or (ns-resolve task-ns task)
         error-fn)
     (catch java.io.FileNotFoundException e
       error-fn))))

(defn cli-main [& [task & args]]
  (cond 
   (= task "repl") (clojure.main/main)
   (or (= task "help")
       (nil? task)) (println "TODO: add useful help tools, don't necessarily want *.clj files tightly coupled to cli")
   :else (apply (resolve-task task) args)))
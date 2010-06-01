(ns dj.cli.help
  "display a list of tasks or help for a given task"
  (:require [dj.classloader :as cl])
  (:import [java.util.jar JarFile])
  (:import [java.io File FileNotFoundException]))

(defn task-ns [task]
  (find-ns (symbol (str "dj.cli." task))))

(defn task-doc [task]
  (:doc (meta (ns-resolve (task-ns task) 'main))))

(defn recursive-list
  "return seq of files of namespace dj.cli.*"
  [f]
  (when (.isDirectory f)
    (if (= "dj" (.getName f))
      (let [cli-dir (File. f "cli")]
	(when (.exists cli-dir)
	  (.listFiles cli-dir)))
      (doall (mapcat recursive-list (.listFiles f))))))

(defn load-tasks!
  "needed because of the help command needs all documentation loaded before hand"
  []
  (doseq [f cl/+boot-classpaths+]
    (if (.isDirectory f)
      (doall (map #(load-file (.getCanonicalPath %)) (recursive-list f)))
      (doall (for [e (enumeration-seq (.entries (JarFile. f)))
		   :let [filename (.getName e)
			 g (re-find #"dj/cli/(.*)\.clj" filename)]
		   :when g]
	       (require (symbol (str "dj.cli." (second g))) :reload))))))

(defn main
  "USAGE: [task-name]

   Given task-name, returns task documentation"
  ([]
     (load-tasks!)
     (println "USAGE: dj task-name [args*]\n")
     (println "Availabile tasks:")
     (doseq [s (sort
		(for [ns (all-ns)
		      :let [g (re-find #"^dj\.cli\.(.*)" (name (ns-name ns)))]
		      :when g]
		  (str (second g) " - " (:doc (meta ns)))))]
       (println s)))
  ([& [task]]
     (load-tasks!)
     (println (task-doc task))))

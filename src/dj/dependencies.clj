(ns dj.dependencies
  (:require [leiningen.core.project :as project]
	    [dj]
	    [dj.io]
	    [dj.git]))

(defn project-read-str
  "like leiningen.core.project/read but from a string instead of a file"
  ([proj-str profiles]
     (binding [*ns* (the-ns 'leiningen.core.project)]
       (eval (read-string proj-str)))
     (let [project (resolve 'leiningen.core.project/project)]
       (when-not project
	 (throw (Exception. "project.clj must define project map.")))
       ;; return it to original state
       (ns-unmap 'leiningen.core.project 'project)
       (-> (reduce project/apply-middleware @project (:middleware @project))
	   (project/merge-profiles profiles))))
  ([proj-str] (project-read-str proj-str [:default])))

(defmulti resolve-dj-dependency :dependency-type)

(defn parse-dj-project-dependency [entry]
  (if (= java.lang.String (type entry))
    (let [components (.split #"/" entry)]
      (if (re-find #"http://|git://|https://|ssh://" entry)
	(let [[_ n] (re-find #"((?:\w|-|_)+)\.git" (last components))]
	  {:dependency-type :git
	   :name n
	   :git-path entry})
	(if (= "clojure" (first components))
	  {:dependency-type :git
	   :name entry
	   :git-path (str "git://github.com/" entry ".git")}
	  {:dependency-type :source
	   :name (last components)
	   :relative-path entry})))
    entry))

(defn resolve-project [relative-path]
  (let [project-dir (dj.io/file dj/system-root "usr/src" relative-path)
	project-data (-> (dj.io/file project-dir "project.clj")
			 slurp
			 project-read-str
			 (assoc :root (dj.io/get-path project-dir)
				:eval-in :leiningen))]
    (if-let [dj-dependencies (:dj/dependencies project-data)]
      (doall (map (comp resolve-dj-dependency parse-dj-project-dependency) dj-dependencies))
      (project/init-project project-data))))
;; we want to be able to resolve a project, then we can learn to resolve a git repo

;; keywords might clash, one potential fix is to used namespaced
;; keywords, i can guarantee that all the keywords are not fully
;; qualified


;; first I need the local path, this is from the name?  i'd like a way
;; to do namespace as in nested folders, but these git folders are
;; more like remote trackers, as long as you acknowledge that
;; limitation than its fine, you can do more custom version controlled
;; content with source types
(defmethod resolve-dj-dependency :git [entry-obj]
	   (let [f (dj.io/file dj/system-root "usr/src" (:name entry-obj))]
	     (when-not (dj.io/exists? f)
	       (dj.git/clone (:git-path entry-obj)))
	     (resolve-project (dj.io/get-path f))))

;; i don't want to fully commit to "usr/src"
;; i'd like a verbose mode, where a map is passed
(defmethod resolve-dj-dependency :source [entry-obj]
	   (let [relative-path (:relative-path entry-obj)
		 f (if relative-path
		     (dj.io/file dj/system-root "usr/src" relative-path)
		     (dj.io/file dj/system-root (:root-path entry-obj)))]
	     (resolve-project (dj.io/get-path f))))
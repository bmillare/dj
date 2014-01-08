(ns dj.dependencies
  (:require [leiningen.core.project :as project]
            [cemerick.pomegranate :as pom]
	    [dj]
	    [dj.io]
	    [dj.git]
            [clojure.set :as cs]))

(dj/import-fn #'pom/add-dependencies)

(defn eval-project-form
  "like leiningen.core.project/read but from a clojure form instead of a file"
  [project-form profiles project-file]
  (locking eval-project-form
    (binding [*ns* (the-ns 'leiningen.core.project)
              *file* (dj.io/get-path project-file)]
      (eval project-form))
    (let [project (resolve 'leiningen.core.project/project)]
      (when-not project
        (throw (Exception. "project.clj must define project map.")))
      ;; return it to original state
      (ns-unmap 'leiningen.core.project 'project)
      (project/init-profiles (project/project-with-profiles @project) profiles))))

(defmulti resolve-dj-dependency :dependency-type)

(defn parse-dj-project-dependency [entry]
  (if (= java.lang.String (type entry))
    (let [components (.split #"/" entry)]
      (if (re-find #"http://|git://|https://|ssh://" entry)
	(let [[_ n] (re-find #"((?:\w|-|_|\.)+)" (last components))]
	  {:dependency-type :git
	   :name (if (= (dj/substring n -4)
                        ".git")
                   (dj/substring n 0 -4)
                   n)
	   :git-path entry})
	(if (= "clojure" (first components))
	  {:dependency-type :git
	   :name entry
	   :git-path (str "git://github.com/" entry ".git")}
	  {:dependency-type :source
	   :name (last components)
	   :relative-path entry})))
    entry))

(defn resolve-path [path]
  (let [f (dj.io/file path)]
    (if (.isAbsolute f)
      f
      (dj.io/file dj/system-root "usr/src" path))))

(defn resolve-project [relative-path]
  (let [project-dir (resolve-path relative-path)
        project-file (dj.io/file project-dir "project.clj")
	project-data (-> project-file
			 slurp
			 read-string
			 (eval-project-form [:default] project-file)
			 (assoc :root (dj.io/get-path project-dir)
				:eval-in :leiningen))]
    (if-let [dj-dependencies (:dj/dependencies project-data)]
      (do
	(doall (map (comp resolve-dj-dependency parse-dj-project-dependency) dj-dependencies))
	(project/init-project project-data))
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
	   (let [f (resolve-path (:name entry-obj))]
             (when-not (dj.io/exists? f)
	       (dj.git/clone (:git-path entry-obj)))
	     (resolve-project (dj.io/get-path f))))

;; i don't want to fully commit to "usr/src"
;; i'd like a verbose mode, where a map is passed
(defmethod resolve-dj-dependency :source [entry-obj]
	   (let [relative-path (:relative-path entry-obj)
		 f (if relative-path
		     (resolve-path relative-path)
		     (dj.io/file dj/system-root (:root-path entry-obj)))]
	     (resolve-project (dj.io/get-path f))))

(defn project-source-dependencies* [dependency:queue dependency:return dependency:visited]
  (let [d (first dependency:queue)]
    (if d
      (if (dependency:visited (:name d))
        (project-source-dependencies* (rest dependency:queue)
                                      dependency:return
                                      dependency:visited)
        (let [dependency:type (:dependency-type d)
              project-file (-> ((case dependency:type
                                          :git :name
                                          :source :relative-path) d) 
                                       resolve-path
                                       (dj.io/file "project.clj"))
              dependency:expansion (-> project-file
                                       slurp
                                       read-string
                                       (eval-project-form [:default] project-file)
                                       :dj/dependencies
                                       (->> (map parse-dj-project-dependency)))]
          (project-source-dependencies* (concat dependency:expansion
                                                (rest dependency:queue))
                                        (conj dependency:return
                                              d)
                                        (conj dependency:visited
                                              (:name d)))))
      dependency:return)))

(defn project-source-dependencies
  "return a list of dependencies that are from source, git or local"
  [relative-path]
  (project-source-dependencies* (list (parse-dj-project-dependency relative-path))
                                []
                                #{}))

(defn update-source-dependencies [relative-path]
  (->> relative-path
       project-source-dependencies
       (reduce (fn [ret d]
                 (let [local-file (-> ((case (:dependency-type d)
                                         :source :relative-path
                                         :git :name) d)
                                      dj.git/proj)]
                   (if (dj.io/exists? (dj.io/file local-file ".git"))
                     (assoc ret
                       d
                       (dj.git/pull local-file))
                     (assoc ret
                       d
                       nil))))
               {})))
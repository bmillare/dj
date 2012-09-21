(ns dj.git
  (:require [dj]
	    [dj.io]))

(defn clone
  ([^java.lang.String uri ^java.io.File dest]
     (let [urish (org.eclipse.jgit.transport.URIish. uri)]
       (doto (org.eclipse.jgit.api.CloneCommand.)
	 (.setURI uri)
	 (.setDirectory (dj.io/file dest (.getHumanishName urish)))
	 (.call))))
  ([^java.lang.String uri]
     (clone uri (dj.io/file dj/system-root "usr/src"))))

;; note for whatever reason, pull'ing and push'ing must be with
;; reference to a file that points to the .git folder, not the parent.

;; this is strange since commit seams to work for the parent folder.
(defn pull [file]
  (-> (.pull (org.eclipse.jgit.api.Git/open file))
    (.call)))

(defn push [file]
  (-> (.push (org.eclipse.jgit.api.Git/open file))
      (.call)))

(defn commit [file options]
  (let [{:keys [all amend author message]} options
	c (.commit (org.eclipse.jgit.api.Git/open file))]
    (when all
      (.setAll c true))
    (.setMessage c (or message "default"))
    (.call c)))

(defn foo [file]
  (.getRepository (org.eclipse.jgit.api.Git/open file)))
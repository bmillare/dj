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

#_ (defn pull []
     (org.eclipse.jgit.api.PullCommand. ))
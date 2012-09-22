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

(defn lookup-with-local-config [hostname]
  (let [host-data (.lookup (org.eclipse.jgit.transport.OpenSshConfig/get org.eclipse.jgit.util.FS/DETECTED)
			   hostname)]
    {:hostname (.getHostName host-data)
     :identity-file (.getIdentityFile host-data)
     :port (.getPort host-data)
     :preferred-authentications (.getPreferredAuthentications host-data)
     :strict-host-key-checking (.getStrictHostKeyChecking host-data)
     :user (.getUser host-data)
     :batch-mode? (.isBatchMode host-data)}))

;; (org.eclipse.jgit.transport.URIish.)
;; we can cache this and query the user, use seesaw
(defn passphrase-cp [items]
  (proxy [org.eclipse.jgit.transport.CredentialsProvider] []
    (get [uri items]
	 (let [items (seq items)]
	   ;; must set CredentialItem item accordingly
	   (doseq [i items]
	     (when (re-find #"Passphrase"
			    (.getPromptText i))
	       (.setValue i (:passphrase items)))))
	 true)
    (isInteractive []
		   true)))

(defmacro with-credential-provider [p & body]
  `(let [current-provider# (org.eclipse.jgit.transport.CredentialsProvider/getDefault)]
     (try
       (org.eclipse.jgit.transport.CredentialsProvider/setDefault ~p)
       ~@body
       (finally
	(org.eclipse.jgit.transport.CredentialsProvider/setDefault current-provider#)))))
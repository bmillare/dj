(ns dj.git
  (:require [dj]
	    [dj.io]
	    [seesaw.core :as sc]))

(defrecord git-logger [log]
  com.jcraft.jsch.Logger
  (isEnabled [this level]
	     true)
  (log [this level msg]
       (swap! log conj msg)))

(def log (->git-logger (atom [])))

(defn request-passphrase
  "Creates input dialog and returns password"
  [msg]
  (let [pw (sc/password :echo-char \*)
	pw-promise (promise)]
    (javax.swing.JOptionPane/showMessageDialog nil (object-array [msg pw]))
    (sc/with-password* pw (fn [p] (deliver pw-promise (apply str p))))
    @pw-promise))

(def m-request-passphrase
     (memoize request-passphrase))

(defprotocol CPSetter
  (set-value [this v]))

(extend-type org.eclipse.jgit.transport.CredentialItem
  CPSetter
  (set-value [this v]
	     (.setValue this (str v))))

(extend-type org.eclipse.jgit.transport.CredentialItem$CharArrayType
  CPSetter
  (set-value [this v]
	     (.setValue this (char-array v))))

(extend-type org.eclipse.jgit.transport.CredentialItem$YesNoType
  CPSetter
  (set-value [this v]
	     (.setValue this (-> (re-find #"(?i)no" (str v))
				 not
				 boolean))))

(defn passphrase-cp []
  (proxy [org.eclipse.jgit.transport.CredentialsProvider] []
    (get [uri items]
	 (let [items (seq items)]
	   (doseq [i items]
	     (set-value i
			(m-request-passphrase
			 (.getPromptText i)))))
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

(defn with-dcp [f & args]
  (with-credential-provider (passphrase-cp)
    (apply f args)))

(defn set-logger [logger]
  (com.jcraft.jsch.JSch/setLogger logger))

(defn clone*
  ([^java.lang.String uri ^java.io.File dest]
     (let [urish (org.eclipse.jgit.transport.URIish. uri)]
       (doto (org.eclipse.jgit.api.CloneCommand.)
	 (.setURI uri)
	 (.setDirectory (dj.io/file dest (.getHumanishName urish)))
	 (.call))))
  ([^java.lang.String uri]
     (clone* uri (dj.io/file dj/system-root "usr/src"))))

(defn clone
  ([uri dest]
     (with-dcp clone* uri dest))
  ([uri]
     (with-dcp clone* uri)))

(defn pull* [file]
  (-> (.pull (org.eclipse.jgit.api.Git/open file))
      (.call)))

(defn pull [file]
  (with-dcp pull* file))

(defn push* [file]
  (-> (.push (org.eclipse.jgit.api.Git/open file))
      (.call)))

(defn push [file]
  (with-dcp push* file))

(defn commit*
  "options must be a map with probably 'all' set to true and 'message'
  set to something"
  [file options]
  (let [{:keys [all amend author message]} options
	c (.commit (org.eclipse.jgit.api.Git/open file))]
    (when all
      (.setAll c true))
    (.setMessage c (or message "default"))
    (.call c)))

(defn commit [file options]
  (with-dcp commit* file options))

(defn diff [file]
  (-> (.diff (org.eclipse.jgit.api.Git/open file))
      (.call)))

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

(def ssh-key-instructions
"You'll need to generate keys. Windows users can use puttygen. *nix
users use ssh-keygen. To use jgit you'll probably need to create
a ~/.ssh/config file which contains

Host *
    StrictHostKeyChecking no

For windows users this usually in C:\\Users\\hara\\.ssh

You may need to install full unrestricted crytography via
http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html

You may want to use github generated key
")
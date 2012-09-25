(ns dj.git
  (:require [dj]
	    [dj.io]))

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
  (let [add-border (fn [component]
		     (doto component
		       (.setBorder
			(javax.swing.BorderFactory/createLineBorder (java.awt.Color. 0 0 0 0)
								    10))))
	ret-pw (promise)
	deliver-password (fn [password]
			   (deliver ret-pw
				    (apply str password))
			   (java.util.Arrays/fill password \0))
	pw (javax.swing.JPasswordField. (int 20))
	focus (fn []
		(javax.swing.SwingUtilities/invokeLater #(dj.repl/log (.requestFocusInWindow pw))))
	panel (doto (javax.swing.Box. javax.swing.BoxLayout/Y_AXIS)
		(.add (doto (javax.swing.JLabel. msg)
			add-border
			(.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)))
		(.add (javax.swing.Box/createVerticalGlue))
		(.add pw))
	frame (doto (javax.swing.JFrame. "Input Required")
		(.setLocationRelativeTo nil)
		(.add (add-border panel))
		(.addWindowListener (reify java.awt.event.WindowListener
					   (windowActivated [this event]
							    (focus))
					   (windowClosed [this event])
					   (windowDeactivated [this event])
					   (windowDeiconified [this event]
							      (focus))
					   (windowIconified [this event])
					   (windowOpened [this event]
							 (focus))
					   (windowClosing [this event]
							  (deliver-password (.getPassword pw))))))]
    (doto pw
      (.setRequestFocusEnabled true)
      (.setFocusable true)
      (.setEchoChar \*)
      (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)
      (.addKeyListener (reify java.awt.event.KeyListener
			      (keyPressed [this ke]
					  (when (= (.getKeyCode ke)
						   java.awt.event.KeyEvent/VK_ENTER)
					    (deliver-password (.getPassword pw))
					    (.dispatchEvent frame (java.awt.event.WindowEvent. frame
											       java.awt.event.WindowEvent/WINDOW_CLOSING))))
			      (keyReleased [this ke])
			      (keyTyped [this ke]))))
    (javax.swing.SwingUtilities/invokeLater (fn []
					      (doto frame
						(.pack)
						(.show))))
    @ret-pw))

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

(defn add [file filepattern]
  (-> (.add (org.eclipse.jgit.api.Git/open file))
      (.addFilepattern filepattern)
      (.call)))

(defn proj
  "Returns project relative file for convenience with api (relative to
  dj/system-root \"usr/src\""
  [relative-project-path]
  (dj.io/file dj/system-root "usr/src" relative-project-path))

(defn lookup-with-local-config
  "useful debugging information"
  [hostname]
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

You may want to use github generated key, they appear to be more
reliable on windows systems
")
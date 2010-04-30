(ns dj.deps
  (:import [java.io File])
  (:import [org.apache.maven.embedder MavenEmbedder Configuration])
  (:import [org.apache.maven.settings Settings]))

;; maven taking to long to figure, making my own dependency finder and http downloader
(defn files []
  (let [maven-home nil
	classloader (.getContextClassLoader (. Thread currentThread))
	container nil
	system-path (System/getProperty "user.dir")
	user-settings-file (File. system-path "etc/maven/settings.xml")
	; also try without global-settings-file, or toolchains
	global-settings-file (File. "/usr/share/maven-bin-2.0/conf/settings.xml")
	user-toolchains-file (File. system-path "etc/maven/toolchains.xml")
	dj-request nil
	request (DefaultMavenExecutionRequest.)
	settings-request (DefaultSettingsBuildingRequest.)]
    (doto request
      (.setGlobalSettingsFile global-settings-file)
      (.setUserSettingsFile user-settings-file))
    (doto settings-request
      (.setGlobalSettingsFile global-settings-file)
      (.setUserSettingsFile user-settings-file)
      (.setSystemProperties (System/getProperties))
      (.setUserProperties (Properties.))) ;change default repo
    
    ))

(defn resolve-dependencies []
  "given dependency form, returns list of dependency forms that the input depends on

expect to implement this as a recursive analyzing of pom file")
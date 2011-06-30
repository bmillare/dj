(ns dj.cli.install
  "install source projects"
  (:use [dj.core :only [system-root]])
  (:use [dj.toolkit :only [rm new-file str-path]])
  (:use [clojure.java.shell :only [sh]]))

(defn main
  "USAGE: dj install <git-address> [relative-install-path]"
  [& args]
  (let [git-address (first args)
	install-path (second args)]
    (if git-address
      (let [{:keys [out err]} (sh "git" "clone" git-address
				  :dir (.getPath (new-file system-root "usr/src" install-path)))]
	(print err)
	(print out)
	(shutdown-agents))
      (println "USAGE: dj install <git-address> [relative-install-path]"))))
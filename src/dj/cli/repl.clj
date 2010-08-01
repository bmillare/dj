(ns dj.cli.repl
  "start a repl"
  (:require [dj.deps])
  (:require [dj.classloader :as classloader])
  (:require [dj.cli])
  (:require [dj.core])
  (:require [dj.repository]))

(defn main
  "dj repl [project-name]
   if no project-name given, starts a generic repl

   if project-name given, starts repl with classpath set so
   dependencies can be fulfilled, ie. you should have access to the
   projects sources and jar dependencies"
  [& args]
  (if-let [project-name (first args)]
    (let [project-data (->
			project-name
			dj.cli/project-name-to-file
			dj.cli/read-project)
	  {native-jars :jars
	   native-libs :libs} (dj.deps/get-all-native! (:native-dependencies project-data))
	  get-canon (fn [#^java.io.File f] (.getCanonicalPath f))]
      (dj.classloader/with-new-classloader
	[(get-canon (java.io.File. dj.core/system-root (str "usr/src/" project-name "/src")))]
	(vec
	 (map get-canon
	      (concat (for [d (dj.deps/get-all-dependencies! (:dependencies project-data)
							     {:hooks [(dj.deps/resolved-hook nil dj.deps/exclude-id)
								      (dj.deps/exclude-hook (:exclusions project-data) dj.deps/exclude-id)]})]
			(dj.repository/get-dependency-path d ".jar"))
		      native-jars)))
	(vec
	 (map get-canon (set (map #(.getParentFile %) native-libs))))
	(clojure.main/main)))
    (clojure.main/main)))


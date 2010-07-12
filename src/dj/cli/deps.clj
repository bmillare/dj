(ns dj.cli.deps
  "preview what dependencies will be installed"
  (:require [dj.deps])
  (:require [dj.cli :as cli]))

(defn main
  "dj deps project-name

  recursively determine dependencies for project on the fly"
  [& [project-name]]
  (let [project-data (->
		      project-name
		      cli/project-name-to-file
		      cli/read-project)]
    (dj.deps/get-all-dependencies! (concat (:dependencies project-data)
					       (:native-dependencies project-data))
				   {:hooks [(dj.deps/resolved-hook nil dj.deps/exclude-id)
					    (dj.deps/exclude-hook (:exclusions project-data) dj.deps/exclude-id)]
				    :pretend true})))
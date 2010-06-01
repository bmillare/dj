(ns dj.cli.deps
  "preview what dependencies will be installed"
  (:require [dj.deps])
  (:require [dj.cli :as cli]))

(defn main
  "dj deps project-name

  recursively determine dependencies for project on the fly"
  [& [project-name]]
  (binding [dj.deps/options (merge dj.deps/options {:pretend true})]
    (->
     project-name
     cli/project-name-to-file
     cli/read-project
     :dependencies
     dj.deps/get-all-dependencies!
     prn)))
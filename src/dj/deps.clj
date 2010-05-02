(ns dj.deps)

(defn resolve-dependencies []
  "given dependency form, returns list of dependency forms that the input depends on

expect to implement this as a recursive analyzing of pom file")

;; defn obtain direct dependencies for artifact, include an exclusion list that will remove elements from list
;; defn obtain all dependencies for artifact, includes exclusion
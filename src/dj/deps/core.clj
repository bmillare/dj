(ns dj.deps.core
  (:use [dj.toolkit :only [new-file]])
  (:use [dj.core :only [system-root]]))

(defprotocol ADependency
  "allow dj to resolve and fulfill dependency"
  (obtain [d options] "installs only itself into repository, returns file to add to classpath")
  (depends-on [d] "returns dependencies of d")
  (load-type [d] "returns type of dependency for loading #{:src :jar :native}")
  (exclusions [d] "returns a vector of exclusion rules, rules are
  functions that return true or false when given a dependency"))

;; problem: type information can potentially be unknown but it usually
;; isn't. Still want flexbility of adding new dependency forms without
;; messing with existing code input will usually be a vector with
;; certain components, not an object, making dispatching on type

(defmulti parse
  "parse text form and return object form"
  (fn [obj & [type-info]]
    (or type-info (class obj))))

(def repositories-directory (new-file system-root "usr"))
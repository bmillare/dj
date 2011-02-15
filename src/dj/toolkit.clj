(ns dj.toolkit
  (:require [clojure.pprint])
  (:require [clojure.repl])
  (:require [clojure.java.javadoc])
  (:require [clojure.java.shell :as sh]))

(load "toolkit/core")
(load "toolkit/code")
(load "toolkit/repl")
(load "toolkit/io")

(defn classpaths
  []
  (dj.classloader/get-classpaths user/*classloader*))

(defn pwd
  []
  (.getCanonicalPath (java.io.File. ".")))

(defn spit-form [^java.io.File file form]
  (with-open [w (java.io.FileWriter. file)]
    (binding [*out* w *print-dup* true]
      (prn form)))
  form)

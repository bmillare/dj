(ns dj.toolkit
  (:require [clojure.pprint])
  (:require [clojure.repl])
  (:require [clojure.java.javadoc])
  (:require [clojure.java.shell :as sh])
  (:require [clojure.string])
  (:import [javax.swing JPanel JFrame JTextField JButton JLabel])
  (:import [java.awt GridBagLayout GridBagConstraints Insets])
  (:import [javax.swing.event DocumentListener])
  (:import [javax.swing SwingUtilities]))

(load "toolkit/core")
(load "toolkit/code")
(load "toolkit/repl")
(load "toolkit/io")
(load "toolkit/string")
(load "toolkit/viewer")

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

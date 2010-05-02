(ns dj.core
  (:import [java.io File]))

(def system-root (File. (System/getProperty "user.dir")))

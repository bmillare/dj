(ns dj.io
  (:require [dj]
	    [clojure.java.io]
	    [clojure.java.shell :as sh])
  (:refer-clojure :exclude [pr-str read-string print-method]))

(load "io/protocols")
(load "io/core")
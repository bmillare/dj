(ns dj.deps.native
  (:require [dj.io])
  (:require [dj.cli])
  (:use [dj.deps.core])
  (:use [dj.core :only [system-root]]))

(defrecord native-dependency [name version group])

(defmethod parse :native-dependency [obj & [_]]
	   (let [id (first obj)
		 version (second obj)]
	     (native-dependency. (name id)
				version
				(or (namespace id)
				    (name id)))))

;; implement later
(extend native-dependency
  ADependency
  {:obtain (fn [d _]
	     nil)
   :depends-on (fn [d]
		 nil)
   :load-type (fn [d] :native)
   :exclusions (fn [d]
		 nil)})
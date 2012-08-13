(ns dj.repl
  (:require [clojure.pprint]))

(defn unmap-ns
  "unmap everything from a ns"
  [ns]
  (doseq [s (keys (ns-interns ns))]
    (ns-unmap ns s)))

(defmacro log
  "for debugging, output code and code->val to stdout or optional writer, returns val,
custom-fn accepts two arguments, the code, and the result, it must
return a string"
  ([code]
     `(let [c# ~code]
	(prn '~code)
	(clojure.pprint/pprint c#)
	c#))
  ([code writer custom-fn]
     `(let [c# ~code
	    w# ~writer]
	(.write w#
		(~custom-fn '~code c#))
	(.flush w#)
	c#)))
(ns dj.repl
  (:require [clojure.pprint]))

(defn unmap-ns
  "unmap everything from a ns"
  [ns]
  (doseq [s (keys (ns-interns ns))]
    (ns-unmap ns s)))

(defmacro log*
  [code store log-fn]
  `(let [c# ~code]
     (swap! ~store
	    ~log-fn
	    {'~code c#})
     c#))

(defmacro log
  "for debugging, output code and code->val to stdout and returns val. Also supports options
:store (atom and arbitrary data structure)
:log-fn (will swap! value in from log-fn)
"
  ([code]
     `(let [c# ~code]
	(prn '~code)
	(clojure.pprint/pprint c#)
	c#))
  ([code options]
     `(let [options# ~options]
	(log* ~code
	      (:store options#)
	      (or (:log-fn options#)
		  conj)))))

(defmacro log->thread [x form chain-sym]
  `(let [out# (-> ~x
		  ~form)]
     (swap! ~chain-sym
	    conj
	    ['~form out#])
     out#))

(defmacro log->
  "Like -> but logs the value at each step."
  [options x & forms]
  (let [chain-sym (gensym "chain")]
    `(let [options# ~options
	   s# (:store options#)
	   log-fn# (:log-fn options#)
	   x# ~x
	   ~chain-sym (atom [['~x x#]])
	   out# (-> x#
		    ~@(map (fn [form]
			     `(log->thread ~form ~chain-sym))
			   forms))]
       (swap! s#
	      log-fn#
	      (into [['~x x#]]
		    (mapv (fn [[[_# pv#] [f# v#]]]
			    [(macroexpand-1 (list '-> pv# f#)) v#])
			  (partition 2 1 @~chain-sym))))
       out#)))


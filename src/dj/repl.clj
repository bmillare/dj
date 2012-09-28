(ns dj.repl
  (:require [clojure.pprint]))

(defn unmap-ns
  "unmap everything from a ns"
  [ns]
  (doseq [s (keys (ns-interns ns))]
    (ns-unmap ns s)))

(defn logger [store]
  (fn [entity attribute value]
    (swap! store
	   conj
	   {:entity entity
	    :attribute attribute
	    :value value
	    :time (java.util.Date.)})))

(def store (atom []))

(def log*
     "default logger. uses dj.repl/store as the store"
     (logger store))

(defn log-code-macro* [code logger-sym]
  `(let [c# ~code]
     (~logger-sym '~code
		  :returned
		  c#)
     c#))

(defmacro def-log-code-macro
  "creates a code logging macro. Takes a name and a symbol of the
  logger fn (no quote necessary but please fully qualify) that must
  accept an entity, attribute, and value"
  [logger-name logger-sym]
  `(let [logger-sym# '~logger-sym]
     (defmacro ~logger-name
       ~(str "generated loging macro for the " logger-sym " logger")
       [~'code]
       (log-code-macro* ~'code logger-sym#))))

(def-log-code-macro log dj.repl/log*)

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


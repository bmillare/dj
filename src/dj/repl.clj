(ns dj.repl
  (:require [clojure.pprint]
            [dj.io]
            [dj]))

(defn loader
  "loads cljs file relative to project directory"
  ([project-name name-file]
     ;; for whatever reason, load-file on code that alters the
     ;; classpath doesn't get respected, need to load code with eval
     ;; manually
     (eval (read-string (str "(do "
                             (dj.io/eat (dj.io/file dj/system-root "usr/src" project-name (str name-file ".clj")))
                             ")"))))
  ([project-name]
     (loader project-name "loader")))

(defn unmap-ns
  "unmap everything from a ns"
  [ns]
  (doseq [s (keys (ns-interns ns))]
    (ns-unmap ns s)))

(defn map-logger [store]
  (fn [ret-val m]
    (swap! store
	   into
           (let [m' (assoc m
                      :return ret-val)]
             (reduce-kv (fn [ret k v]
                          (conj ret [m'
                                     k
                                     v]))
                        []
                        m')))
    ret-val))

(defn logger
  "returns a function that stores a 4-tuple of entity, attribute,
value and time instance in the store"
  [store]
  (fn [entity attribute value]
    (swap! store
	   conj
           [entity
            attribute
            value
            (java.util.Date.)])))

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

(defmacro loge [entity code]
  `(let [c# ~code]
     (dj.repl/log* ~entity
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

(defprotocol Lifecycle
  (start [component])
  (stop [component]))
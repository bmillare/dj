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

(defn deflogger* [code store]
  `(let [r# ~code]
     (swap! ~store conj r#)
     r#))

(defmacro deflogger [name store]
  `(~'defmacro ~name
     [~'code]
     (deflogger* ~'code '~store)))

(defmacro deftracer
  "

define your own tracing macro in your namespace that is bound to your
walker
"
  [name trace-walker]
  `(let [tw# ~trace-walker]
     (~'defmacro ~name
       ([~'code]
          (tw# ~'code))
       ([~'code ~'tuples]
          (tw# ~'code 0 ~'tuples)))))

(defn ->tuple-trace-logger [store tx-counter]
  (fn [tuples]
    `(let [t# ~tuples]
       (let [tx# (swap! ~tx-counter inc)]
         (swap! ~store
                into
                (mapv (fn [tuple#]
                        (conj tuple# tx#))
                      t#))))))

(defn ->trace-walker
  "

logger-code-fn: side effect fn that takes code, nesting-level and
result of code

returns a code walking fn that calls logger-code-fn before returning
result of code

Current (BUG!) unhandled cases
quote 'this is important so we don't evaluate code that shouldn't be evaluated'

Current partial handling
try
for

Probably should put this in its own library.

For additional extensibility, we can use CPS for case functions, and
allow composing new dispatch cases with these primitives.

For example, this will be important for macroforms that expand into recurs.

"
  ([logger-code-fn depth-limit]
     (->trace-walker logger-code-fn depth-limit (atom 0)))
  ([logger-code-fn depth-limit id-counter]
     (fn trace-walk
       ([code]
          (trace-walk code 0 nil))
       ([code depth tuples]
          (letfn [(wrap-log [modified-code]
                    (swap! id-counter inc)
                    (let [r (gensym "return")
                          s (gensym "start")
                          e (gensym "end")
                          id @id-counter]
                      `(let [~s (java.lang.System/nanoTime)
                             ~r ~modified-code
                             ~e (java.lang.System/nanoTime)]
                         ~(logger-code-fn (into [[id :depth depth]
                                                 [id :code `(quote ~code)]
                                                 [id :result r]
                                                 [id :start-time s]
                                                 [id :end-time e]]
                                                (mapv (fn [[k v]]
                                                        [id k v])
                                                      tuples)))
                         ~r)))
                  (trace-walk-depth [code]
                    (trace-walk code
                                (inc depth)
                                nil))
                  ;; All the cases
                  (trace-hashmap [wrap]
                    (wrap
                     (reduce-kv (fn [m k v]
                                  (assoc m
                                    (trace-walk-depth k)
                                    (trace-walk-depth v)))
                                {}
                                code)))
                  (trace-singleton [wrap]
                    (wrap code))
                  (trace-call [wrap]
                    (if (and (symbol? (first code))
                             (:macro (meta (resolve (first code)))))
                      (trace-walk-depth (macroexpand-1 code))
                      (wrap
                       (list* (first code)
                              (map trace-walk-depth
                                   (rest code))))))
                  (trace-let [wrap]
                    (wrap
                     `(let ~(vec (apply concat
                                        (map (fn [[s e]]
                                               [s (trace-walk e
                                                              (inc depth)
                                                              [[:binding `(quote ~s)]])])
                                             (partition 2 (second code)))))
                        ~@(map trace-walk-depth (drop 2 code)))))
                  (trace-do [wrap]
                    (wrap
                     `(do
                        ~@(map trace-walk-depth (rest code)))))
                  (trace-fn [wrap]
                    (wrap
                     (concat (take 2 code)
                             (map trace-walk-depth (drop 2 code)))))
                  (trace-if-let [wrap]
                    (wrap
                     (concat ['if-let (update-in (second code)
                                                 [1]
                                                 trace-walk-depth)]
                             (map trace-walk-depth (drop 2 code)))))
                  (trace-for [wrap]
                    (wrap
                     (concat (take 2 code) ;; ignoring bindings for now, I'm lazy for all the cases
                             (map trace-walk-depth (drop 2 code)))))
                  (trace-vector [wrap]
                    (wrap
                     (mapv trace-walk-depth code)))
                  (trace-try [wrap]
                    (wrap
                     (concat (take 1 code)
                             (map trace-walk-depth (drop-last (drop 1 code)))
                             [(last code)])))]
            (if (< depth depth-limit)
              (if (coll? code)
                (if (map? code)
                  (trace-hashmap wrap-log)
                  (if (vector? code)
                    (trace-vector wrap-log)
                    (case (first code)
                      let (trace-let wrap-log)
                      let* (trace-let identity)
                      do (trace-do identity)
                      fn (trace-fn wrap-log)
                      if-let (trace-if-let wrap-log)
                      for (trace-for wrap-log)
                      doseq (trace-for wrap-log)
                      dotimes (trace-for wrap-log)
                      try (trace-try wrap-log)
                      -> code ;; ignore for now, I'm lazy for all the cases
                      recur (trace-call identity) ;; note we can't have a let around recur since it would no longer make it tail recursive
                      (trace-call wrap-log)))) ;; <- probable future extension point
                ;; Note for extension, should have everything you could want
                ;; trace-call will call extend-fn and pass it everything
                ;; [current wrapper, id-counter, code, depth, etc]
                ;; if the extend-fn wants to recur, it can call trace-walk
                (trace-singleton wrap-log))
              code))))))

(defprotocol Lifecycle
  (start [component])
  (stop [component]))
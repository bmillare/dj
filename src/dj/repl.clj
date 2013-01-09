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

(defmacro deftracer
  "

define your own tracing macro in your namespace that is bound to your
walker
"
  [name trace-walker]
  `(let [tw# ~trace-walker]
     (~'defmacro ~name [~'code]
       (tw# ~'code))))

(defn ->simple-trace-logger [store]
  (fn [id code depth r]
    `(swap! ~store
            conj
            {:id ~id
             :depth ~depth
             :code '~code
             :result ~r})))

(defn ->trace-walker
  "

logger-code-fn: side effect fn that takes code, nesting-level and
result of code

returns a code walking fn that calls logger-code-fn before returning
result of code

Current (BUG!) unhandled cases
recur
case
quote 'this is important so we don't evaluate code that shouldn't be evaluated'

Current partial handling
try
for

Future: Should probably add an id generator, this way we can actually
compute the counts

Probably should put this in its own library.

For additional extensibility, we can use CPS for case functions, and
allow composing new dispatch cases with these primitives.

For example, this will be important for macroforms that expand into recurs.

"
  [logger-code-fn depth-limit]
  (let [id-counter (atom 0)]
    (fn trace-walk-depth
      ([code]
         (trace-walk-depth code 0))
      ([code depth]
         (let [;; wrap counting
               trace-walk (fn [code]
                            (trace-walk-depth code (inc depth)))
               ;; All the cases
               trace-hashmap (fn []
                               (reduce-kv (fn [m k v]
                                            (assoc m
                                              (trace-walk k)
                                              (trace-walk v)))
                                          {}
                                          code))
               trace-vector (fn []
                              (mapv trace-walk code))
               trace-singleton (fn []
                                 code)
               trace-call (fn []
                            (list* (first code)
                                   (map trace-walk
                                        (rest code))))
               trace-let (fn []
                           `(let ~(mapv (fn [i e]
                                          (if (odd? i)
                                            (trace-walk e)
                                            e))
                                        (range (count (second code)))
                                        (second code))
                              ~@(map trace-walk (drop 2 code))))
               trace-do (fn []
                          `(do
                             ~@(map trace-walk (rest code))))
               trace-fn (fn []
                          (concat (take 2 code)
                                  (map trace-walk (drop 2 code))))
               trace-if-let (fn []
                              (concat ['if-let (update-in (second code)
                                                          [1]
                                                          trace-walk)]
                                      (map trace-walk (drop 2 code))))
               trace-for (fn []
                           (concat (take 2 code) ;; ignoring bindings for now, I'm lazy for all the cases
                                   (map trace-walk (drop 2 code))))
               trace-thread (fn []
                              code)
               r (gensym "return")
               wrap (fn [f]
                      (swap! id-counter inc)
                      `(let [~r ~(f)]
                         ~(logger-code-fn @id-counter
                                          code
                                          depth
                                          r)
                         ~r))]
           ;; dispatching logic
           (if (< depth depth-limit)
             (if (coll? code)
               (if (map? code)
                 (wrap trace-hashmap)
                 (if (vector? code)
                   (wrap trace-vector)
                   (case (first code)
                     let (wrap trace-let)
                     do (wrap trace-do)
                     fn (wrap trace-fn)
                     if-let (wrap trace-if-let)
                     for (wrap trace-for)
                     try (fn [] code) ;; ignore for now, I'm lazy for all the cases
                     -> (wrap trace-thread)
                     recur (trace-call) ;; note we can't have a let around recur since it would no longer make it tail recursive
                     (wrap trace-call)))) ;; <- probable future extension point
               (wrap trace-singleton))
             code))))))

(defprotocol Lifecycle
  (start [component])
  (stop [component]))
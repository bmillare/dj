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

(defn ->datomic-trace-logger [store]
  (fn [tuples]
    `(swap! ~store
            into
            ~tuples)))

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
    (fn trace-walk
      [code]
      (letfn [ ;; wrap counting
              trace-walk-depth (fn [code]
                                 (trace-walk code
                                             (update-in state
                                                        [:depth]
                                                        inc)))
              r (gensym "return")
              wrap-log (fn wrap
                         [modified-code]
                         (swap! id-counter inc)
                         `(let [~r ~modified-code]
                            ~(logger-code-fn (into [[@id-counter :code `(quote ~code)]
                                                    [@id-counter :depth depth]
                                                    [@id-counter :result r]]
                                                   (mapv (fn [t]
                                                           (into [@id-counter]
                                                                 t))
                                                         (:tuples state))))
                            ~r))
              ;; All the cases
              trace-hashmap (fn []
                              (wrap-log
                               (reduce-kv (fn [m k v]
                                            (assoc m
                                              (trace-walk-depth k)
                                              (trace-walk-depth v)))
                                          {}
                                          code)))
              trace-singleton (fn []
                                (wrap-log code))
              trace-call-no-log (fn []
                                  (list* (first code)
                                         (map trace-walk-depth
                                              (rest code))))
              trace-call (fn []
                           (wrap-log
                            (trace-call-no-log)))
              trace-let (fn []
                          (wrap-log
                           `(let ~(vec (apply concat
                                              (map (fn [[s e]]
                                                     [s (trace-walk e
                                                                    (-> state
                                                                        (update-in [:depth]
                                                                                   inc)
                                                                        (assoc :tuples
                                                                          [:binding `(quote ~s)])))])
                                                   (partition 2 (second code)))))
                              ~@(map trace-walk-depth (drop 2 code)))))
              trace-do (fn []
                         (wrap-log
                          `(do
                             ~@(map trace-walk-depth (rest code)))))
              trace-fn (fn []
                         (wrap-log
                          (concat (take 2 code)
                                  (map trace-walk-depth (drop 2 code)))))
              trace-if-let (fn []
                             (wrap-log
                              (concat ['if-let (update-in (second code)
                                                          [1]
                                                          trace-walk-depth)]
                                      (map trace-walk-depth (drop 2 code)))))
              trace-for (fn [code c]
                          (wrap-log
                           (concat (take 2 code) ;; ignoring bindings for now, I'm lazy for all the cases
                                   (map trace-walk-depth (drop 2 code)))))
              (trace-ignore [state d ]
                            (c (-> state
                                   (assoc :code (:input state)))))
              trace-vector (fn [state d c]
                             (wrap-log
                              (mapv trace-walk-depth code)))
              (dispatch [state]
                        (if (< (:depth state)
                               depth-limit)
                          (if (coll? code)
                            (if (map? code)
                              trace-hashmap
                              (if (vector? code)
                                trace-vector
                                (case (first code)
                                  let trace-let
                                  do trace-do
                                  fn trace-fn
                                  if-let trace-if-let
                                  for trace-for
                                  try trace-ignore ;; ignore for now, I'm lazy for all the cases
                                  -> trace-ignore
                                  recur trace-call-no-log ;; note we can't have a let around recur since it would no longer make it tail recursive
                                  trace-call))) ;; <- probable future extension point
                            trace-singleton)
                          trace-ignore))]
                  (let [state {:code nil
                               :input code
                               :depth 0}]
                    ((dispatch state)
                     state
                     :code))))))

(defprotocol Lifecycle
  (start [component])
  (stop [component]))
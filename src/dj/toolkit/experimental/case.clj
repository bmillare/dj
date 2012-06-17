(ns dj.toolkit.experimental.case)

(defrecord case-fn [case-map-atom dispatch-fn-atom query-fn]
  clojure.lang.IFn
  (invoke [this arg] (@dispatch-fn-atom (query-fn arg) arg)))

(defmacro defcase-fn [name query-fn]
  `(def ~name (case-fn. (atom {})
			(atom (fn [_# __#] "No methods defined"))
			~query-fn)))

(defn defcase* [case-fn-obj dispatch-value method-fn]
  (let [{:keys [case-map-atom dispatch-fn-atom]} case-fn-obj]
    (swap! case-map-atom assoc dispatch-value method-fn)
    (reset! dispatch-fn-atom
	    (eval (let [this (gensym "this")]
		    `(fn [val# ~this]
		       (case val#
			     ~@(mapcat (fn [[k f]]
					 [k `(~f ~this)])
				       (seq @case-map-atom)))))))
    dispatch-value))

(defmacro defcase [name dispatch-value arglist & body]
  `(let [dv# ~dispatch-value]
     (defcase* ~name dv# (fn ~arglist ~@body))))


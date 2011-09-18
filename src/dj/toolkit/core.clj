(in-ns 'dj.toolkit)

(defn plurality
  "return the first largest count item in all-items"
  [& all]
  (first (reduce (fn [[k0 v0] [k1 v1]]
		   (if (> v1 v0)
		     [k1 v1]
		     [k0 v0]))
		 (persistent! (reduce (fn [counts item]
					(if (counts item)
					  (assoc! counts item (inc (counts item)))
					  (assoc! counts item 1)))
				      (transient {})
				      all)))))

;; the doalls are necessary or else the maps are realized all at once at the end,
;; I want them realized as we traverse the seq
(defn min-max-by-columns [s]
  (reduce (fn [[smallest largest] y]
	    [(doall (map min smallest y)) (doall (map max largest y))])
	  [(first s) (first s)]
	  s))

(defn update-all-in
  "returns map of application of fn f to all values in map m"
  [m f]
  (reduce #(update-in %1
		      [%2]
		      f)
	  m
	  (keys m)))

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

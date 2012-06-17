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

;; Taken from Zachary Tellman's Potemkin
(defmacro import-fn 
  "Given a function in another namespace, defines a function by
   the same name in the current namespace.  Argument lists and
   doc-strings are preserved."
  [sym]
  (let [m (meta (eval sym))
        m (meta (intern (:ns m) (:name m)))
        n (:name m)
        arglists (:arglists m)
        doc (:doc m)]
    (list `def (with-meta n {:doc doc :arglists (list 'quote arglists)}) (eval sym))))

(defn filter-fns
  "like filter, but takes a list of classifier functions instead of a
  single classifier"
  [fns rows]
  (filter #(every?
	    (fn [f]
	      (f %))
	    fns)
	  rows))
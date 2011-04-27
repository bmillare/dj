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
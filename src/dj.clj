(ns dj
  (:require [clojure.walk]))

(def system-root (java.io.File. (System/getProperty "user.dir")))

(defn str-path
  "joins paths defined in strings together (unix)"
  [parent & children]
  (apply str (if (= (first parent)
		    \/)
	       "/"
	       "")
	 (interpose "/" (filter #(not (empty? %)) (mapcat #(.split (str %) "/") (list* parent children))))))

(defn duplicates
  "given a sequable list, returns a vector of the duplicate entries in
  the order that they were found"
  [s]
  (loop [ns s
	 test #{}
	 duplicates []]
    (if-let [x (first ns)]
      (if (test x)
	(recur (next ns)
	       test
	       (conj duplicates x))
	(recur (next ns)
	       (conj test x)
	       duplicates))
      duplicates)))

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

(defmacro group-by-for
  "Equivalent to applying 'for' to the results of 'group-by' but also
provides let bindings, is more efficient since it executes in one pass
and uses transients.

:let is optional but must be after 'entry coll' binding
:group-by (Required) must be an expression

Example usage:

 (group-by-for [entry (range 5)
                :let [x (inc entry)]
                :group-by (odd? x)]
   (str x))
=> {true [\"1\" \"3\" \"5\"], false [\"2\" \"4\"]}
"
  [[entry coll & binding-exprs] result-expr]
  (let [[letk let-expr
         group-byk group-by-expr]
        (case (count binding-exprs)
          2 (into [:let []] binding-exprs)
          4 binding-exprs
          (throw
           (Exception. "Illegal number of binding-exprs: "
                       (count binding-exprs))))]
    (when-not (= letk :let)
      (throw
       (Exception. ":let form not in correct place")))
    (when-not (= group-byk :group-by)
      (throw
       (Exception. ":group-by form not in correct place")))
    `(persistent!
      (reduce (fn [ret# ~entry]
                (let ~let-expr
                  (let [k# ~group-by-expr]
                    (assoc! ret#
                            k#
                            (conj (get ret#
                                       k#
                                       [])
                                  ~result-expr)))))
              (transient {})
              ~coll))))

(defn update-vals
  "returns hashmap of application of f (with optional args) to all
values in hashmap m

Example usage:
 (update-vals {:a 1 :b 2} + 3)
=> {:a 4 :b 5}
"
  ([m f & args]
     (persistent!
      (reduce-kv (fn [ret k v]
                   (assoc! ret k (apply f v args)))
                 (transient {})
                 m)))
  ([m f]
     (persistent!
      (reduce-kv (fn [ret k v]
                   (assoc! ret k (f v)))
                 (transient {})
                 m))))

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

(defn replace-map-unchecked
  "given an input string and a hash-map, returns a new string with all
  keys in map found in input replaced with the value of the key. DOES
  NOT java.util.regex.Matcher/quoteReplacement replace strings"
  [s m]
  (clojure.string/replace s
			  (re-pattern (apply str (interpose "|" (map #(java.util.regex.Pattern/quote %) (keys m)))))
			  m))

(defn replace-map
  "given an input string and a hash-map, returns a new string with all
  keys in map found in input replaced with the value of the key"
  [s m]
  (replace-map-unchecked s (reduce #(assoc %1 %2
				      (java.util.regex.Matcher/quoteReplacement (%1 %2)))
				   m
				   (keys m))))

(defn re-find-all
  "given a regular expression re, and an input string txt, returns a vector of
  all sequential matches of re in txt"
  [^java.util.regex.Pattern re ^java.lang.CharSequence txt]
  (let [m (.matcher re txt)]
    (loop [matches []]
      (if (.find m)
	(recur (conj matches (.group m)))
	matches))))

(defn substring
  "extends builtin subs to have negative indexes. A negative index
  will implicitly mean "
  ([s start end]
     (let [s-size (count s)
	   s-idx (if (< start 0)
		   (+ s-size start)
		   start)
	   e-idx (if (< end 0)
		   (+ s-size end)
		   end)]
       (subs s s-idx e-idx)))
  ([s start]
     (let [s-size (count s)
	   s-idx (if (< start 0)
		   (+ s-size start)
		   start)]
       (subs s s-idx))))

(defn- bang-symbol?
  "Returns true, if sym is a symbol with name ending in a exclamation
  mark (bang)."
  [sym]
  (and (symbol? sym)
       (= (last (name sym)) \!)))

(defmacro defmacro!
  "Defines a macro name with the given docstring, args, and body.
  All args ending in an exclamation mark (!, bang) will be evaluated only once
  in the expansion, even if they are unquoted at several places in body.  This
  is especially important for args whose evaluation has side-effecs or who are
  expensive to evaluate."
  [name docstring args & body]
  (let [bang-syms (filter bang-symbol? args)
        rep-map (apply hash-map
                       (mapcat (fn [s] [s `(quote ~(gensym))])
                               bang-syms))]
    `(defmacro ~name
       ~docstring
       ~args
       `(let ~~(vec (mapcat (fn [[s t]] [t s]) rep-map))
          ~(clojure.walk/postwalk-replace ~rep-map ~@body)))))
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
  "Equivalent to applying 'for' to the results of 'group-by' but it
also provides let bindings, is more efficient since it executes in one
pass, and uses transients.

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
  ([m f x]
     (persistent!
      (reduce-kv (fn [ret k v]
                   (assoc! ret k (f v x)))
                 (transient {})
                 m)))
  ([m f x y]
     (persistent!
      (reduce-kv (fn [ret k v]
                   (assoc! ret k (f v x y)))
                 (transient {})
                 m)))
  ([m f x y & args]
     (persistent!
      (reduce-kv (fn [ret k v]
                   (assoc! ret k (apply f v x y args)))
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
  "Given a function in another namespace, defines a function by the
same name in the current namespace.  Argument lists and doc-strings
are preserved."
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
means to the left of the end of the string "
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
All args ending in an exclamation mark (!, bang) will be evaluated
only once in the expansion, even if they are unquoted at several
places in body.  This is especially important for args whose
evaluation has side-effecs or who are expensive to evaluate."
  [name
   docstring args & body]
  (let [bang-syms (filter bang-symbol? args)
        rep-map (apply hash-map
                       (mapcat (fn [s] [s `(quote ~(gensym))])
                               bang-syms))]
    `(defmacro ~name
       ~docstring
       ~args
       `(let ~~(vec (mapcat (fn [[s t]] [t s]) rep-map))
          ~(clojure.walk/postwalk-replace ~rep-map ~@body)))))

(defmacro compile-time-if
  "Eval's check at compile time, if true, returns form1,
else returns form2."
  [check form1 form2]
  (if (eval check)
    form1
    form2))

(defmacro var-let
  "like let but each binding is to a var. All var names are
  initialized to nil before hand so that they are forward
  referenced. This allows circular definitions which is useful for
  things like defining grammars."
  [bindings & body]
  (let [pairs (partition 2 bindings)
        symbols (map first pairs)]
    `(let ~(vec (apply concat (for [s symbols]
                                (list s '(clojure.lang.Var/create)))))
       ~@(for [[s v] pairs]
           `(.bindRoot ^clojure.lang.Var
                       ~s
                       ~v))
       ~@body)))

(defn index-of
  "
given something indexable, and predicate, returns index of first true
"
  ([v predicate accessor-fn]
     (loop [idx 0]
       (if (predicate (accessor-fn v idx))
         idx
         (recur (inc idx)))))
  ([v predicate]
     (index-of v predicate nth)))

(defn hold
  "returns a hold, which can be deref'd its value only once it has
  been allowed to release"
  [v]
  (let [d (java.util.concurrent.CountDownLatch. 1)]
    (reify
      clojure.lang.IDeref
      (deref [_]
        (.await d)
        v)
      clojure.lang.IPending
      (isRealized [this]
        (zero? (.getCount d)))
      clojure.lang.IFn
      (invoke
        [this]
        (when (pos? (.getCount d))
          (.countDown d))
        this))))
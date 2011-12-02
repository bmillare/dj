(ns dj.toolkit.experimental.peg
  (:require dj))

;; ()
;; +*/
;; 0-9
;; letters

;; terminals and non-terminals

;; (1) The following utilities are parser generators. (2) The
;; generators accept configuration arguments. (3) The parser accepts
;; input, success/fail functions, and success/fail function state. (4)
;; The parser will then return a closure (a function that calls the
;; other parsers depending on the parsers input or the success/fail
;; functions) so that trampoline can be used and there won't be a
;; stack overflow. (4) success/fail functions accept the string that
;; failed or succeeded, the remaining input and the current maintained
;; state

;; trampoline will be called only on nonterminals

(defmacro bounce
  "use instead of calling a function, this will create a closure for
  use with trampoline to avoid stackoverflows due to mutual recursion"
  [f & form]
  `(fn [] (~f ~@form)))

(defn token
  "returns parser that looks for token that matches re"
  [^java.util.regex.Pattern re]
  (fn [input pass fail]
    (let [m (re-matcher re input)]
      ;; .lookingAt returns true if re matches input
      (if (.lookingAt m)
	;; .group returns matched string
	;; .end returns index of end character
	(pass (.group m) (subs input (.end m)))
	(fail nil input)))))

;; The seq' parser generator returns a parser that calls pass only if
;; all parsers succeed. The state that will be passed to pass will be
;; a sequence of the states returned by all the other parsers.
(defn seq'
  "returns a parser that chains parsers"
  ([m n]
     (fn [input pass fail]
       (bounce
	m
	input
	(fn [m-pass-state m-input]
	  (bounce
	   n
	   m-input
	   (fn [n-pass-state n-input]
	     (pass [m-pass-state n-pass-state] n-input))
	   fail))
	fail)))
  ([m n & args]
     ;; The more than 2 parser case is tricky because we want the
     ;; passed state to be a flat vector and not nested. We need to
     ;; change the way we join our states after we call seq' since
     ;; that state is already a vector. Therefore, we now call conj.
     (let [seq'' (fn [m' n']
		   (fn [input pass fail]
		     (bounce
		      m'
		      input
		      (fn [m'-pass-state m'-input]
			(bounce
			 n'
			 m'-input
			 (fn [n'-pass-state n'-input]
			   (pass (conj m'-pass-state n'-pass-state) n'-input))
			 fail))
		      fail)))]
       (reduce seq'' (seq' m n) args))))

(defn choice
  "returns a parser that calls success on the first succeeded parser"
  ([m n]
     (fn [input pass fail]
       (bounce
	m
	input
	pass
	(fn [_ _]
	  (bounce
	   n
	   input
	   pass
	   fail)))))
  ([m n & args]
     (reduce choice m (list* n args))))

(defn star
  "returns a parser that always succeeds on n number of calls to parser x on input"
  [x]
  (fn [input pass fail]
    ;; Like in the seq' case, we have to correctly accumulate
    ;; state. Here on the first successful parsing we put the state in
    ;; a vector. From then on, all successful parses gets conjed onto
    ;; that original vector.
    (letfn [(first-continue [old-pass-state old-input]
			    (bounce
			     x
			     old-input
			     (fn [new-pass-state new-input]
			       (continue [old-pass-state new-pass-state] new-input))
			     (fn [new-pass-state new-input]
			       (pass [old-pass-state] new-input))))
	    (continue [old-pass-state old-input]
		      (bounce
		       x
		       old-input
		       (fn [new-pass-state new-input]
			 (continue (conj old-pass-state new-pass-state) new-input))
		       (fn [new-pass-state new-input]
			 (pass old-pass-state new-input))))]
      (bounce
       x
       input
       first-continue
       pass))))

(defn plus [x]
  (fn [input pass fail]
    (letfn [(first-continue [old-pass-state old-input]
			    (bounce
			     x
			     old-input
			     (fn [new-pass-state new-input]
			       (continue [old-pass-state new-pass-state] new-input))
			     (fn [new-pass-state new-input]
			       (pass [old-pass-state] new-input))))
	    (continue [old-pass-state old-input]
		      (bounce
		       x
		       old-input
		       (fn [new-pass-state new-input]
			 (continue (conj old-pass-state new-pass-state) new-input))
		       (fn [new-pass-state new-input]
			 (pass old-pass-state new-input))))]
      (bounce
       x
       input
       first-continue
       fail))))

(defn not?
  "negative lookahead, returns parser that parses without consuming input"
  [x]
  (fn [input pass fail]
    (bounce
     x
     input
     (fn [_ _]
       (fail nil input))
     (fn [_ _]
       (pass nil input)))))

(defn and?
  "and lookahead, returns parser that parses without consuming input"
  [x]
  (fn [input pass fail]
    (bounce
     x
     input
     (fn [_ _]
       (pass nil input))
     (fn [_ _]
       (fail nil input)))))

(defn opt
  "returns parser that optionally accepts input"
  [x]
  (fn [input pass fail]
    (bounce
     x
     input
     pass
     (fn [_ _]
       (pass nil input)))))

(defn wrap-pass
  "wrap pass continuation with code"
  [x wrap-fn]
  (fn [input old-pass fail]
    (bounce
     x
     input
     (wrap-fn old-pass)
     fail)))

(defn state-transform
  "returns a wrapped version of parser p that modifies state before
  passing it to the pass function"
  [p state-transformer-fn]
  (fn [input pass fail]
    (bounce
     p
     input
     (fn [state remaining-input]
       (pass (state-transformer-fn state) remaining-input))
     fail)))

#_ (do
     (let [printer (fn [outcome]
		     (fn [state input]
		       (println outcome)
		       (println "Matches:" state)
		       (println "Remaining Input:" input)))
	   f (token #"function")
	   n (token #"\d")
	   e (token #"bend")]
       (trampoline (seq' f (star n) (opt e)) "function34end" (printer "success") (printer "fail"))
       )
     (let [printer (fn [outcome]
		     (fn [state input]
		       (println outcome)
		       (println "Matches:" state)
		       (println "Remaining Input:" input)))
	   value (wrap-pass (token #"\d+")
			    (fn [pass]
			      (fn [state input]
				(pass (list (Integer/parseInt (first state))) input))))
	   sumo (wrap-pass (seq' value (token #"\+") value)
			   (fn [pass]
			     (fn [state input]
			       (pass (list (+ (first state)
					      (last state))) input))))]
       (trampoline sumo "a+4" (printer "success") (printer "fail"))
       )
     )
(ns dj.toolkit.experimental.peg
  (:refer-clojure :exclude [seq]))

;; Note: This file is written in a pseudo literate programming (LP)
;; style. Instead of relying on the LP tools to expand and reorganize
;; the code, I rely on the reader to navigate the code using the
;; search function :)

;; PEG: This a functional Parser Expression Grammar library designed
;; for writing composable parsers.

;; To understand the model, there are 4 types of functions in this
;; library you need to understand:

;; 1. Parsers Generators
;; 2. Parsers
;; 3. Success and Fail continuation functions
;; 4. Continuation wrappers

;; You will also use the trampoline wrappers, but they are simply a
;; convenience function for calling the parsers.

;; To start, lets look at a simple parser generator, token.

(defn token
;; This is the first example parser generator. Given a regular
;; expression, it returns a parser that will succeed if there is a
;; match (see .lookingAt to understand exactly what constitutes a
;; match), else it will fail. To succeed or fail, means to call
;; succeed or call fail. This continuation style programming, and
;; allows us to easily modify our control flow. I call the parser that
;; token returns, a 'Basic' parser, since it does not delegate to
;; other '2. Parsers', in other words it does not delegate to other
;; parsers that conform to our argument signatures. This means we must
;; convert what it means for a java re matcher to succeed and fail, to
;; our convention.
  "returns parser that looks for token that matches re"
  [^java.util.regex.Pattern re]
  (fn [input succeed fail]
    (let [m (re-matcher re input)]
      ;; We do the translation using .lookingAt, which attempts to
      ;; match the input sequence, starting at the beginning of the
      ;; region, against the pattern. It does not require the whole
      ;; input match, but only from the start a match can be found. It
      ;; returns true if it succeeds, else false. We take this
      ;; information and then call the appropriate continuation.
      (if (.lookingAt m)
	;; Another important aspect of parsers is how they manage the
	;; results of the parsing. On success, this parser will return
	;; the matched string using .group. We must also manage what
	;; input we consumed, and return what remains to be consumed.
	
	(succeed (.group m)             ;; .group returns the matched string
		 (subs input (.end m))) ;; .end returns the index of the end character
	(fail nil input)))))

;; This PEG library uses clojure's trampolines to limit our
;; consumption of the memory stack. To do this, we need to change the
;; way we call our functions. Instead of directly calling the
;; function, we return a closure which calls then calls the
;; function. Trampoline will automatically call the next closure as
;; appropriate. To make it more clear that we are returning a closure
;; for trampoline, and not a parser or continuation, I've written a
;; macro, bounce, that does the closure wrapping. Semantically, this
;; is a good name, since we effectively are bouncing on the trampoline
;; to make the function call.

(defmacro bounce
  "use instead of calling a function, this will create a closure for
  use with trampoline to avoid stackoverflows due to mutual recursion"
  [f & form]
  `(fn [] (~f ~@form)))

;; Although this does reduce our stack consumption, it does clutter
;; our code. As a compromise, I will only call bounce on
;; nonterminals. We will see its first usage in our first delegating
;; parser, seq

(defn seq
 "The seq parser generator returns a parser that succeeds only if all
 parsers succeed. The result that will be passed to succeed will be a
 vector of the results returned by all the other parsers."
  ([m n]
     (fn [input succeed fail]
       (bounce
	m
	input
	;; If we succeed, we check to see if the next parser succeeds.
	(fn [m-result m-rest-input]
	  (bounce
	   n
	   m-rest-input
	   (fn [n-result n-rest-input]
	     ;; The result is a vector of all the results
	     (succeed [m-result n-result] n-rest-input))
	   fail))
	fail)))
  ([m n & args]
     ;; The more than 2 parser case is tricky because we want the
     ;; passed result to be a flat vector and not nested. We need to
     ;; change the way we join our results after we call seq since
     ;; that result is already a vector. Therefore, we now call conj.
     (let [seq' (fn [m' n']
		   (fn [input succeed fail]
		     (bounce
		      m'
		      input
		      (fn [m'-result m'-rest-input]
			(bounce
			 n'
			 m'-rest-input
			 (fn [n'-result n'-rest-input]
			   (succeed (conj m'-result n'-result) n'-rest-input))
			 fail))
		      fail)))]
       (reduce seq' (seq m n) args))))

(defn choice
  "returns a parser that calls success on the first succeeded parser"
  ([m n]
     (fn [input succeed fail]
       (bounce
	m
	input
	succeed
	(fn [_ _]
	  (bounce
	   n
	   input
	   succeed
	   fail)))))
  ([m n & args]
     (reduce choice m (list* n args))))

(defn star
  "returns a parser that always succeeds on n number of calls to
  parser x on input"
  [x]
  (fn [input succeed fail]
    ;; Like in the seq case, we have to correctly accumulate
    ;; state. Here on the first successful parsing we put the state in
    ;; a vector. From then on, all successful parses gets conjed onto
    ;; that original vector.
    (letfn [(first-continue [old-succeed-state old-input]
			    (bounce
			     x
			     old-input
			     (fn [new-succeed-state new-input]
			       (continue [old-succeed-state new-succeed-state] new-input))
			     (fn [new-succeed-state new-input]
			       (succeed [old-succeed-state] new-input))))
	    (continue [old-succeed-state old-input]
		      (bounce
		       x
		       old-input
		       (fn [new-succeed-state new-input]
			 (continue (conj old-succeed-state new-succeed-state) new-input))
		       (fn [new-succeed-state new-input]
			 (succeed old-succeed-state new-input))))]
      (bounce
       x
       input
       first-continue
       succeed))))

(defn plus [x]
  (fn [input succeed fail]
    (letfn [(first-continue [old-succeed-state old-input]
			    (bounce
			     x
			     old-input
			     (fn [new-succeed-state new-input]
			       (continue [old-succeed-state new-succeed-state] new-input))
			     (fn [new-succeed-state new-input]
			       (succeed [old-succeed-state] new-input))))
	    (continue [old-succeed-state old-input]
		      (bounce
		       x
		       old-input
		       (fn [new-succeed-state new-input]
			 (continue (conj old-succeed-state new-succeed-state) new-input))
		       (fn [new-succeed-state new-input]
			 (succeed old-succeed-state new-input))))]
      (bounce
       x
       input
       first-continue
       fail))))

(defn not?
  "negative lookahead, returns parser that parses without consuming
  input"
  [x]
  (fn [input succeed fail]
    (bounce
     x
     input
     (fn [_ _]
       (fail nil input))
     (fn [_ _]
       (succeed nil input)))))

(defn and?
  "and lookahead, returns parser that parses without consuming input"
  [x]
  (fn [input succeed fail]
    (bounce
     x
     input
     (fn [_ _]
       (succeed nil input))
     (fn [_ _]
       (fail nil input)))))

(defn opt
  "returns parser that optionally accepts input"
  [x]
  (fn [input succeed fail]
    (bounce
     x
     input
     succeed
     (fn [_ _]
       (succeed nil input)))))

;; The peg library is designed like Ring. The following functions are
;; middleware, where they return a new parser

(defn alter-result
  "returns a wrapped version of parser p that modifies result before
  passing it to the succeed function"
  [p result-alter-fn]
  (fn [input succeed fail]
    (bounce
     p
     input
     (fn [result rest-input]
       (succeed (result-alter-fn result) rest-input))
     fail)))

;; Our default trampoline wrapper
(defn parse
  "calls the parser on input with default continuation functions. On
  succeeds, returns a vector of the result and the remaining input. On
  fail, throws and exception with the current result and remaining input."
  [parser input]
  (trampoline parser
	      input
	      (fn [result rest-input]
		[result rest-input])
	      (fn [result rest-input]
		(throw (Exception. (str "Parse failed with result: "
					result " and remaining input: "
					rest-input))))))
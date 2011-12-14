(ns dj.toolkit.peg
  (:refer-clojure :exclude [seq]))

;; Note: This file is written in a pseudo literate programming (LP)
;; style. Instead of relying on the LP tools to expand and reorganize
;; the code, I rely on the reader to navigate the code using the
;; search function :)

;; Summary: This a functional Parser Expression Grammar library designed
;; for writing composable parsers.

;; Advantages: There are quite a few advantages to PEG parsers. They
;; are strictly more powerful than regular expressions, since there is
;; recursion, you can parse nested parentheses. Because the choice
;; operator is creates a preference over two paths, PEG parsers are
;; never ambiguous. So for example, they will always be able to parse
;; the "dangling else" problem found in C, C++ and Java. When combined
;; with functional programming techniques, it becomes very easy to
;; write small, composable parsers, eliminating the need to define
;; grammars in a separate format and call large parser generators such
;; as lex and yacc. (See parser combinators from Haskell).

;; Disadvantages: Without memoization, these parsers can have
;; asymptotic performance. With memoization, they can consume much
;; more memory but will run in linear time. For many cases in
;; practice, however, this is not an issue. A more serious issue is
;; the problem of indirect left recursion. This library makes no
;; attempt to solve these problems automatically. I assume that the
;; user of the library will be aware of these issues and rework their
;; grammars as appropriate. Another side effect of using recursive
;; functions is the consumption of stack (since I make no assumption
;; that you are using clojure on a tail call optimizing version of the
;; JVM). I solve this problem using clojure's trampolines.

;; To understand the programming model, there are 4 types of functions
;; in this library you need to understand:

;; 1. Parsers Generators
;; 2. Parsers
;; 3. Success and Fail continuation functions
;; 4. Continuation wrappers

;; You will also use the trampoline wrappers, but they are simply a
;; convenience function for calling the parsers.

;; Overview:

;; This document should flow linearly from start to finish. At the end
;; of the document, make sure you should understand token, any of the
;; parser/parser generators, alter-result, and parse.

;; Token is our model terminal parser.

;; The parser generators/parsers are the bread and butter for users of
;; this library. Those are the functions you will call to construct
;; your grammars.

;; alter-result is the default continuation wrapper that lets us
;; actually do useful work after parsing. Users of this library will
;; need to interleave this to different parts of their sub-parsers.

;; Finally, parse is the main invocation point that wraps a trampoline
;; call and provides default success and fail continuations.

;; To start, lets look at a simple parser generator, token.

(defn token
;; This is the first example parser generator. Given a regular
;; expression, it returns a parser that will succeed if there is a
;; match (see .lookingAt to understand exactly what constitutes a
;; match), else it will fail. To succeed or fail, means to call
;; succeed or call fail. This continuation style programming, and
;; allows us to easily modify our control flow. I call the parser that
;; token returns, a terminal parser, since it does not delegate to
;; other parsers that conform to our function contracts. This stops
;; the recursion and thus 'terminates' the parsing. This, also implies
;; that we must translate what it means for a java re matcher to
;; succeed and fail, to our convention, and wrap that up into a parser
;; function.
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

;; NOTE: On writing your own terminal parsers. It's very easy to write
;; your own terminal parsers. In the above example I treat a string as
;; a sequence of regular expression matches. That's a little bit more
;; high level than just treating the string as a sequence of
;; characters. The later may be simpler, but the former has advantages
;; in that you can usually parse text more efficiently and it is
;; easier to express common idioms in regular expressions. This
;; library is general in that if you write your own terminal parsers
;; on different input types like number sequences, the non-terminal
;; parsers should "just work" on them.

;; As mentioned before, functional parsers can consume a lot of stack
;; on non tail call optimizing compilers. This PEG library uses
;; clojure's trampolines to limit our consumption of the memory
;; stack. To do this, we need to change the way we call our
;; functions. Instead of directly calling the function, we return a
;; closure which then calls the function. When you use trampoline to
;; invoke our parsers, it will automatically call the next closure
;; returned by the parser until the closures no longer returns another
;; closure. This will happen when our highest most continuation
;; function is called.

;; To make it more clear that we are returning a closure for
;; trampoline, and not a parser or continuation, I've written a macro,
;; bounce, that does the closure wrapping. Semantically, this is a
;; good name, since we effectively are bouncing on the trampoline to
;; make the function call.

(defmacro bounce
  "use instead of calling a function, this will create a closure for
  use with trampoline to avoid stackoverflows due to mutual recursion"
  [f & form]
  `(fn [] (~f ~@form)))

;; Although this does reduce our stack consumption, it does clutter
;; our code. As a compromise, I will only call bounce on nonterminal
;; parsers. We will see its first usage in our first delegating
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
	   (fn [_ _]
	     (fail nil input))))
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
			 (fn [_ _]
			   (fail nil input))))
		      fail)))]
       (reduce seq' (seq m n) args))))

;; I won't go into detail about the remaining PEG operators, choice,
;; star, plus, not?, and?, and opt since you can learn about the
;; purpose on wikipedia. I may mention programming issues that came up
;; though.

(defn choice
  "returns a parser that calls succeed on the first succeeded parser"
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
     (reduce choice (choice m n) args)))

(defn star
  "returns a parser that always succeeds on n number of calls to
  parser x on input"
  [x]
  (fn [input succeed fail]
    ;; Like in the seq case, we have to correctly accumulate
    ;; state. Here on the first successful parsing we put the state in
    ;; a vector. From then on, all successful parses gets conjed onto
    ;; that original vector.
    (letfn [(first-continue [old-result old-rest-input]
			    (bounce
			     x
			     old-rest-input
			     (fn [new-result new-rest-input]
			       (continue [old-result new-result] new-rest-input))
			     (fn [new-result new-rest-input]
			       (succeed [old-result] new-rest-input))))
	    (continue [old-result old-rest-input]
		      (bounce
		       x
		       old-rest-input
		       (fn [new-result new-rest-input]
			 (continue (conj old-result new-result) new-rest-input))
		       (fn [new-result new-rest-input]
			 (succeed old-result new-rest-input))))]
      (bounce
       x
       input
       first-continue
       succeed))))

(defn plus [x]
  (fn [input succeed fail]
    (letfn [(first-continue [old-result old-rest-input]
			    (bounce
			     x
			     old-rest-input
			     (fn [new-result new-rest-input]
			       (continue [old-result new-result] new-rest-input))
			     (fn [new-result new-rest-input]
			       (succeed [old-result] new-rest-input))))
	    (continue [old-result old-rest-input]
		      (bounce
		       x
		       old-rest-input
		       (fn [new-result new-rest-input]
			 (continue (conj old-result new-result) new-rest-input))
		       (fn [new-result new-rest-input]
			 (succeed old-result new-rest-input))))]
      (bounce
       x
       input
       first-continue
       fail))))

;; Note that not? and and? do not consume input. I've decided to not
;; pass any useful result information to the continuation functions
;; since I can't think of pratical use for this. If you desire this,
;; you can discuss this with me. Also you can always write new look
;; ahead parser generators.
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

(defn parse
;; This is the default trampoline wrapper. You use this function to
;; invoke a parser at the toplevel.  Example: (peg/parse (peg/token
;; #"\d+") "234")
  "calls the parser on input with default continuation functions. On
  success, returns a vector of the result and the remaining input. On
  failure, throws and exception with the current result and remaining
  input. Uses trampolines underneath to limit stack consumption. You
  can also supply your own succeed and fail continuation functions."
  ([parser input]
     (trampoline parser
		 input
		 (fn [result rest-input]
		   [result rest-input])
		 (fn [result rest-input]
		   (throw (Exception. (str "Parse failed with result: "
					   result " and remaining input: "
					   rest-input))))))
  ([parser input succeed fail]
     (trampoline parser
		 input
		 succeed
		 fail)))

;; The peg library takes some inspiration from Ring. The function
;; alter-result is like middleware in that it wraps the old parser, do
;; some data manipulation, and return a new parser.

(defn alter-result
;; To me this is the most useful continuation wrapper. One good
;; example, you want to parse a number, so you write a token parser
;; with (peg/token #"\d+"). You want the result to be an actual
;; number, so you wrap it with java's integer
;; parser. (peg/alter-result (peg/token #"\d+") #(Integer/parseInt %))
;; Now, when you invoke it, succeed gets passed an Integer instead of
;; a string.
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


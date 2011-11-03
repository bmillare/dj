(ns dj.toolkit.experimental.meta
  (:require [dj.toolkit :as tk])
  (:require [clojure.java.shell :as sh]))

(defn flatten-1 [coll]
  (loop [x coll
	 r ()]
    (if (empty? x)
      r
      (let [v (first x)]
	(if (sequential? v)
	  (recur (rest x) (concat r v))
	  (recur (rest x) (concat r (list v))))))))

(defn recursive-ls [file]
  (flatten (for [f (tk/ls file)]
	     (if (.isDirectory f)
	       (list* f (recursive-ls f))
	       f))))

(defonce history (atom ()))
;; input history, output history

(def username (System/getProperty "user.name"))

(def scan-directories [(tk/new-file "/home" username "Documents")
		       (tk/new-file "/home" username "Downloads")
		       (tk/new-file "/home" username "Pictures")])

(defn filter-path [re files]
  (filter #(re-find re (.getPath %)) files))

(defn filter-name [re files]
  (filter #(re-find re (.getName %)) files))

(defn file [f-or-path]
  (let [f (tk/new-file f-or-path)]
    (swap! history conj f)
    f))

(defn toog* [query files]
  (let [terms (map #(re-pattern (java.util.regex.Pattern/quote %)) (.split #" " query))]
    (filter (fn [path]
	      (every? #(re-find % (.getPath path))
		      terms))
	    files)))

(defn ls-print [files]
  (println "Files:")
  (doseq [f files]
    (println (.getPath f))))

(defprotocol Icompile
  (compile* [this]))

(extend-type java.io.File
  Icompile
  (compile* [this]
	    (.getPath this)))

(extend-type String
  Icompile
  (compile* [this]
	    this))

(extend-type java.lang.Object
  Icompile
  (compile* [this]
	    (if (sequential? this)
	      (map compile* this)
	      (throw (Exception. (pr-str "Invalid input" this))))))

(defn sh* [dir forms]
  (apply sh/sh (concat (flatten-1 (map compile* forms)) [:dir dir])))

(defonce current-directory (atom (tk/new-file "/home")))

(defn user-space []
  (flatten (map #(recursive-ls %) scan-directories)))

;; Public API

(defn new-downloads []
  (take 5 (reverse (sort-by #(.lastModified %) (tk/ls (tk/new-file "/home" username "Downloads"))))))

(defn toog
  ([query]
     (toog query user-space))
  ([query files-fn]
     (let [results (toog* query (files-fn))]
       (if (= (count results) 1)
	 (first results)
	 results))))

(defn p [f & args]
  (let [result (apply f args)]
    (if (= (type result)
	   java.io.File)
      (let []
	(println "Single File:")
	(println result))
      (let []
	(ls-print result)))))

(defn cd [file-or-path]
  (reset! current-directory (tk/new-file file-or-path)))

(defn pwd []
  @current-directory)

(defn sh [& forms]
  (future (sh* @current-directory forms)))

#_ (do
     (in-ns 'dj.toolkit.experimental.meta)
     (p toog "exp pdf")
     (p toog "exp Doc")
     (clojure.repl/pst)
     (p toog "pdf" #(do @history))
     (sh "echo" "hello")
     (cd "/home/bmillare")
     (sh "evince" (toog "exp pdf Aon 2007"))
     (p toog "is" new-downloads)
     (cd (first (toog "classes/pp Documents")))
     (sh "mv"
	 (toog "pdf" (partial tk/ls @current-directory))
	 (toog "lectures" (partial tk/ls @current-directory)))
     (p (partial tk/ls @current-directory))
     (cd (tk/new-file @current-directory "lectures"))
     (p toog "pp/lectures")
     (sh "evince" (toog "pp/lectures lec10"))
     (p toog "picture")
     (sh "scp" "bmillare@boh.icm.jhu.edu:~/grandma-*tar.gz" (tk/new-file "/home/hara/Pictures"))
     (p toog "ictures")
     (p tk/ls (tk/new-file "/home/hara/Pictures"))
     (.getParent (toog "grand tar"))
     (sh "mv" (toog "more pic") "/home/hara/Pictures")
     (cd "/home/hara/Pictures")
     (p tk/ls @current-directory)
     (print (:out @(sh "ls" (toog "hara/Downloads zip"))))

     (flatten-1 [1 2 [3 4] [5 6] [7 8 [9 10]] 3])

     (in-ns 'dj.toolkit.experimental.meta)
     )


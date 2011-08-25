(ns dj.toolkit.experimental.db
  (:refer-clojure :exclude [read])
  (:require [dj.toolkit.experimental.digest :as digest]
	    [dj.toolkit :as tk]))

(defn print-dup-str [obj]
  (let [sw (java.io.StringWriter.)]
    (binding [*print-dup* true]
      (print-dup obj sw))
    (.toString sw)))

(defmacro simple-defrecord-print-ctor* [object-name fields]
  (let [class-name (.getName ^Class (resolve object-name))
	class-name-split (vec (.split #"\." class-name))
	name (str (apply str (interpose "." (drop-last 1 class-name-split)))
		  "/new-"
		  (nth class-name-split (dec (count class-name-split))))
	w* (gensym "w")
	o* (gensym "o")]
    `(defmethod print-dup ~(symbol class-name)
		[~o* ~(with-meta w* {:tag 'java.io.Writer})]
		(.write ~w* "(")
		(.write ~w* ~name)
		(.write ~w* " ")
		~@(interpose `(.write ~w* " ") (map (fn [field]
						      `(print-dup (~field ~o*)
								  ~w*))
						    fields))
		(.write ~w* ")"))))

(defmacro defprintable-ctor [name symbols]
  (let [w (gensym "w")]
    `(do
       (defn ~(symbol (str "new-" name)) ~symbols
	 (new ~name ~@symbols))
       (simple-defrecord-print-ctor* ~name [~@(map keyword symbols)]))))

;; Main concepts are namespace and id

(defprotocol Icompute-id
  (compute-id [obj]))

(extend-type java.lang.Object
  Icompute-id
  (compute-id [this] (digest/sha-256 (print-dup-str this))))

(extend-type nil
  Icompute-id
  (compute-id [this] (digest/sha-256 (print-dup-str this))))

(defprotocol Idb
  (read-obj [this id])
  (write-obj [this obj id])
  (resolve-pvar [this namespace name])
  (intern-pvar [this obj namespace name]))

(def ^:dynamic *db* nil)

;; Note loops do exist
(defrecord local-link [id]
  clojure.lang.IDeref
  (deref
   [this]
   ;; May want to make this recursive instead and remove *db*, this way can implement a lazy version
   (read-obj *db* id)))

(defprintable-ctor local-link [id])

(prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref)
(prefer-method print-method clojure.lang.IPersistentMap clojure.lang.IDeref)

(defrecord code [form]
  clojure.lang.IDeref
  (deref
   [this]
   (eval form)))

(defn new-code [form]
      (new code form))
(defmethod print-dup code
  [o w]
  (.write w "(")
  (.write w "dj.toolkit.experimental.db/new-code")
  (.write w " ")
  (print-dup `(quote ~(:form o)) w)
  (.write w ")"))

(defmacro pquote [form]
  `(db/new-code (quote ~form)))

(defprotocol Ipvar-exists?
  (pvar-exists? [db namespace name]))

(defrecord pvar* [namespace name]
  clojure.lang.IDeref
  (deref
   [this]
   (resolve-pvar *db* namespace name)))

(simple-defrecord-print-ctor* pvar* [:namespace :name])

(defn new-pvar* [namespace name]
  (if (pvar-exists? *db* namespace name)
    (pvar*. namespace name)
    (throw (Exception. (str "Unable to resolve symbol: " namespace "/" name " in this context")))))

(defmacro pvar [s]
  (let [namespace (namespace s)
	name (name s)]
    (if namespace
      `(new-pvar* ~namespace ~name)
      `(new-pvar* *p-ns* ~name))))

(defrecord local-db [path]
  Idb
  (read-obj [db id]
	    (let [path (:path db)
		  prefix (subs id 0 2)
		  tail (subs id 2)]
	      (load-file (tk/str-path path ".objects" prefix tail))))
  (write-obj [db obj id]
	     (let [path (:path db)
		   prefix (subs id 0 2)
		   tail (subs id 2)
		   folder (tk/new-file path ".objects" prefix)]
	       (when-not (.exists folder)
		 (tk/mkdir folder))
	       (tk/poop (tk/new-file folder tail)
			(print-dup-str obj))))
  (resolve-pvar [db namespace name]
	     (if (pvar-exists? db namespace name)
	       (load-file (tk/str-path path namespace ".pvars" name))
	       (throw (Exception. (str "Unable to resolve symbol: " namespace "/" name " in this context")))))
  (intern-pvar [db obj namespace name]
	     (let [folder (tk/new-file path namespace ".pvars")]
	       (when-not (.exists folder)
		 (tk/mkdir folder))
	       (tk/poop (tk/new-file folder name)
			(print-dup-str obj))))
  Ipvar-exists?
  (pvar-exists?
   [db namespace name]
   (.exists (tk/new-file path namespace ".pvars" name))))

(defprintable-ctor local-db [path])

;; TODO, make this more like namespaces, add var space, macros for symbols

(defn new! [obj]
  (let [id (compute-id obj)]
    (write-obj *db* obj id)
    (new-local-link id)))

(def ^:dynamic *p-ns* nil)

(defn defponce* [namespace name obj]
  (if (pvar-exists? *db* namespace name)
    nil
    (do (intern-pvar *db* obj namespace name)
	(new-pvar* namespace name))))

(defmacro defponce [s obj]
  (let [namespace (namespace s)
	name (name s)]
    (if namespace
      `(defponce* ~namespace ~name ~obj)
      `(defponce* *p-ns* ~name ~obj))))

(defn defp* [namespace name obj]
  (intern-pvar *db* obj namespace name)
  (new-pvar* namespace name))

(defmacro defp [s obj]
  (let [namespace (namespace s)
	name (name s)]
    (if namespace
      `(defp* ~namespace ~name ~obj)
      `(defp* *p-ns* ~name ~obj))))

(defn alter-pvar* [namespace name fun & args]
  (let [obj (resolve-pvar *db* namespace name)]
    (if (pvar-exists? *db* namespace name)
      (do (intern-pvar *db* (apply fun obj args) namespace name)
	  (new-pvar* namespace name))
      (throw (Exception. (str "Pvar: " (tk/str-path namespace name)  " does not exist"))))))

(defmacro alter-pvar [s fun & args]
  (let [namespace (namespace s)
	name (name s)]
    (if namespace
      `(alter-pvar* ~namespace ~name ~fun ~@args)
      `(alter-pvar* *p-ns* ~name ~fun ~@args))))

(defmacro at [s]
  (let [namespace (namespace s)
	name (name s)]
    (if namespace
      `(resolve-pvar *db* ~namespace ~name)
      `(resolve-pvar *db* *p-ns* ~name))))

#_ (extend-type clojure.lang.Symbol
  clojure.lang.IDeref
  (deref [this]
	 (let [namespace (namespace this)
	       name (name this)]
	   (if namespace
	     (resolve-pvar *db* namespace name)
	     (resolve-pvar *db* *p-ns* name)))))

(defmacro with-db
  "db - database, p-ns - persistant namespace, body - code"
  [db p-ns & body]
  (let [p-ns (str p-ns)]
    `(binding [*db* ~db
	      *p-ns* ~p-ns]
      ~@body)))
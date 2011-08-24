(ns dj.toolkit.experimental.db
  (:refer-clojure :exclude [read])
  (:require [dj.toolkit.experimental.digest :as digest]
	    [dj.toolkit :as tk]))

(defn print-dup-str [obj]
  (let [sw (java.io.StringWriter.)]
    (binding [*print-dup* true]
      (print-dup obj sw))
    (.toString sw)))

(defmacro simple-defrecord-print-ctor* [object-name o fields w]
  (let [class-name (.getName ^Class (resolve object-name))
	class-name-split (vec (.split #"\." class-name))
	name (str (apply str (interpose "." (drop-last 1 class-name-split)))
		  "/new-"
		  (nth class-name-split (dec (count class-name-split))))
	w* (gensym "w")
	o* (gensym "o")]
    `(let [~w* ~w
	   ~o* ~o]
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
       (defmethod print-dup ~name [o# ~(with-meta w {:tag 'java.io.Writer})]
		  (simple-defrecord-print-ctor* ~name o# [~@(map keyword symbols)] ~w)))))

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
  (read-obj [this namespace id])
  (write-obj [this obj namespace id])
  (read-ref [this namespace name])
  (write-ref [this obj namespace name]))

(def ^:dynamic *db* nil)

;; Note loops do exist
(defrecord local-link [namespace id]
  clojure.lang.IDeref
  (deref
   [this]
   ;; May want to make this recursive instead and remove *db*, this way can implement a lazy version
   (read-obj *db* namespace id)))

(prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref)

(defprintable-ctor local-link [namespace id])

(defprotocol Iref-exists?
  (ref-exists? [db namespace id]))

(defrecord local-db [path]
  Idb
  (read-obj [db namespace id]
	    (let [path (:path db)
		  prefix (subs id 0 2)
		  tail (subs id 2)]
	      (load-file (tk/str-path path namespace ".objects" prefix tail))))
  (write-obj [db obj namespace id]
	     (let [path (:path db)
		   prefix (subs id 0 2)
		   tail (subs id 2)
		   folder (tk/new-file path namespace ".objects" prefix)]
	       (when-not (.exists folder)
		 (tk/mkdir folder))
	       (tk/poop (tk/new-file folder tail)
			(print-dup-str obj))))
  (read-ref [db namespace name]
	    (load-file (tk/str-path path namespace name)))
  (write-ref [db obj namespace name]
	     (let [folder (tk/new-file path namespace)]
	       (when-not (.exists folder)
		 (tk/mkdir folder))
	       (tk/poop (tk/new-file folder name)
			(print-dup-str obj))))
  Iref-exists?
  (ref-exists?
   [db namespace id]
   (.exists (tk/new-file path namespace id))))

(defprintable-ctor local-db [path])

(defn write! [namespace obj]
  (let [namespace (str namespace)
	id (compute-id obj)]
    (write-obj *db* obj namespace id)
    (new-local-link namespace id)))

(defn new-ref! [symbol obj]
  (let [namespace (namespace symbol)
	name (name symbol)]
    (if (ref-exists? *db* namespace name)
      (throw (Exception. (str "Ref: " (tk/str-path namespace name)  " already exists")))
      (do (write-ref *db* obj namespace name)
	  obj))))

(defn reset-ref! [symbol obj]
  (let [namespace (namespace symbol)
	name (name symbol)]
    (if (ref-exists? *db* namespace name)
      (do (write-ref *db* obj namespace name)
	  obj)
      (throw (Exception. (str "Ref: " (tk/str-path namespace name)  " does not exist"))))))

(defn alter-ref! [symbol fun & args]
  (let [namespace (namespace symbol)
	name (name symbol)
	obj (read-ref *db* namespace name)]
    (if (ref-exists? *db* namespace name)
      (do (write-ref *db* (apply fun obj args) namespace name)
	  obj)
      (throw (Exception. (str "Ref: " (tk/str-path namespace name)  " does not exist"))))))

(defn read-ref! [symbol]
  (let [namespace (namespace symbol)
	name (name symbol)]
    (read-ref *db* namespace name)))
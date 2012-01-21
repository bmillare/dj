(ns dj.toolkit.deploy
  (:require [clojure.set :as set])
  (:require [clojure.java.shell :as sh])
  (:require [dj.toolkit :as tk]))

;; deploy - A library for building, installing, and running software
;; generically on different types of systems.

;; Model:

;; worker - computer, something capable of storing files and
;; performing work. Every worker must have a directory for 'deploy' to
;; work with. This will be aliased as 'deploy-dir'. This directory
;; must have a file 'index.clj' (described below), a 'repo' directory,
;; for a common place (but not limited to) to store packages, and a
;; 'tmp' directory, for jobs to safely, store intermediates and
;; results.

;; path/[index.clj, repo, tmp]

;; package - a folder containing files. Installing it should only
;; require copying the files and updating the index. The name of the
;; folder does not matter, only its contents.

;; package-id - an identifier that is 1-1 to a particular package

;; index.clj - each worker has a package index, which has a
;; package{name, group and version} package metadata

;;; not final yet
;; dependency - when a package needs to declare a dependency, it will
;; return a map with name, group, and version-classifier. The version
;; will be a type implements a version-accept? interface, which
;; accepts a version, and returns true if version is acceptable,
;; otherwise nil. The reason to do this is for security. There aren't
;; any forms that are evaluated, only reader loading of data types.

;; builder - a function that accepts an install-directory and a
;; index-ref, then it installs the package files in that directory.

;; -------------------------------------------------------------------

(defn read-file
  "clojure.core/load-file evaluates the forms in the file, this
  function only reads them in as data"
  [file]
  (let [data (tk/eat file)]
    (if (empty? data)
      nil
      (read-string data))))

(defn durable-ref
  "returns a ref with a watcher that writes to file contents of ref as
it changes. Write file is considered a 'cache' in that it will try to
be up to date as much as possible, but may occasionaly be out of
date (dirty). The implementation will try to update the file as much
as possible without slowing down the ref. Also takes a default value,
where it will create a file if it doesn't exist, otherwise it will not
overwrite the value."
  ([f default-value]
     (when-not (tk/exists? f)
       (tk/poop f
		(prn-str default-value)))
     (durable-ref f))
  ([f]
     (let [writer-queue (agent nil)]
       ;; When the agent starts writing, the cache is marked clean

       ;; When the ref changes, a call to the writer is made. The cache
       ;; is dirty, no new calls to the writer is made while the cache
       ;; is dirty
       (let [dirty (ref false)
	     r (ref (read-file f))
	     clean (fn []
		     (dosync
		      (ref-set dirty false))
		     (tk/poop f
			      (prn-str @r)))]
	 (add-watch r :writer (fn [k r old-state state]
				(dosync
				 (when-not @dirty
				   (ref-set dirty true)
				   (send-off writer-queue
					     (fn [_]
					       (clean)))))))
	 r))))

;; The make-unique-dir function is needed to build folders on a machine
;; that is unique within a parent folder.
(defn make-unique-dir
  "will attempt to make a directory of any name with parent,
  parent-dir"
  [parent-dir]
  (letfn [(f [attempt]
	     (if attempt
	       (let [id (. System (nanoTime))]
		 (try (let [dir (tk/relative-to parent-dir (str id))]
			(tk/mkdir dir)
			dir)
		      (catch Exception e
			(f (next attempt)))))
	       (throw
		(Exception. "Cannot make unique-dir, tried 5 times"))))]
    (f (range 5))))

(defprotocol IChangePath
  (change-path [f path]))

(extend-type java.io.File
  IChangePath
  (change-path [f path]
	       (java.io.File. path)))

(extend-type dj.toolkit.remote-file
  IChangePath
  (change-path [f path]
	       (assoc f :path path)))

(defn build-install
  "installs package (from package-dir) to worker (deploy-dir),
calls builder with package-dir and then updates package-index. All
  builders must return metadata."
  [deploy-dir index-ref builder]
  (let [install-dir (tk/relative-to deploy-dir "repo")
	package-dir (make-unique-dir install-dir)
	package-metadata (assoc (builder package-dir
					 index-ref)
			   :path (tk/get-path package-dir))
	{:keys [name group version]} package-metadata
	pid {:name name :group group :version version}]
    (dosync (alter index-ref
		   assoc
		   pid package-metadata))))

(defn uninstall
  "removes package from worker"
  [deploy-dir index-ref pid]
  (dosync
   (let [package-metadata (@index-ref pid)]
     (if package-metadata
       (tk/rm
	(change-path deploy-dir
		     (:path package-metadata)))
       (throw (Exception. "Package not found")))
     (alter index-ref dissoc pid))))

(defn build-map
  "given a parent directory, and a map of relative-paths to content,
  creates files with content"
  [parent-dir m]
  (doseq [k (keys m)]
    (tk/poop (tk/relative-to parent-dir k)
	     (m k))))

;; -------------------------------------------------------------------

;; Get run to work,
(defprotocol IShIn
  (sh-in [dir sh-script] "run script in directory"))

(extend-type java.io.File
  IShIn
  (sh-in [dir sh-script]
	 (tk/sh-exception
	  (sh/sh "sh" "-c" sh-script :dir dir))))

(extend-type dj.toolkit.remote-file
  IShIn
  (sh-in [dir sh-script]
	 (tk/sh-exception
	  (let [{:keys [path username server port]} dir]
	    (sh/sh "ssh" (str username "@" server) "-p" (str port)
		   "sh" "-c"
		   (str "'cd " path "; " sh-script "'"))))))
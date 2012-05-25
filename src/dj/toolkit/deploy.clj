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
;; must have a file 'index.clj' (described below), a 'packages' directory,
;; for a common place (but not limited to) to store packages, and a
;; 'tmp' directory, for jobs to safely, store intermediates and
;; results.

;; path/[index.clj, packages, tmp]

;; package - a folder containing files. Installing it should only
;; require copying the files and updating the index. The name of the
;; folder does not matter, only its contents.

;; package-id - an identifier that is 1-1 to a particular package
;; contains the name, group, version, and dependencies

;; index.clj - each worker has a package index, which maps package-ids
;; to metadata pertaining to the installed package

;;; not final yet
;; dependency - when a package needs to declare a dependency, it will
;; return a map with name, group, and version-classifier. The version
;; will be a type implements a version-accept? interface, which
;; accepts a version, and returns true if version is acceptable,
;; otherwise nil. The reason to do this is for security. There aren't
;; any forms that are evaluated, only reader loading of data types.

;; maker - returns a pair, (pid, builder)

;; builder - a function that accepts an install-directory and a
;; index-ref, then it installs the package files in that
;; directory. Builders return additional metadata that will be added
;; in addition to path.

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
		(with-out-str
		  (clojure.pprint/pprint default-value))))
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
			      (with-out-str
				(clojure.pprint/pprint @r))))]
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
	       (java.io.File. ^java.lang.String path)))

(extend-type dj.toolkit.remote-file
  IChangePath
  (change-path [f path]
	       (assoc f :path path)))

(defn build-install
  "installs package (from package-dir) to worker (deploy-dir),
calls builder with package-dir and then updates package-index. All
  builders must return metadata."
  [deploy-dir index-ref {:keys [package-id dependencies builder]}]
  (let [install-dir (tk/relative-to deploy-dir "packages")
	missing-dependencies (seq
			      (filter #(not (@index-ref %))
				      dependencies))]
    (if missing-dependencies
      (throw (Exception. (str "Missing dependencies: "
			      (pr-str missing-dependencies))))
      (if (@index-ref package-id)
	(throw (Exception. (str "Package "
				(pr-str package-id)
				" already exists")))
	(let [package-dir (make-unique-dir install-dir)
	      package-metadata (assoc (builder package-dir
					       index-ref)
				 :path (tk/get-name package-dir))]
	  (dosync
	   (alter index-ref
		  assoc
		  package-id package-metadata)))))))

(defn unreferenced-folders
  "returns folders that aren't referenced in the index"
  [deploy-dir index-ref]
  (let [all-dirs (tk/ls (tk/relative-to deploy-dir "packages"))
	paths (set
	       (map :path
		    (filter :path
			    (vals @index-ref))))]
    (for [d all-dirs
	  :let [path (tk/get-path d)]
	  :when (not (paths path))]
      d)))

(defn uninstall
  "removes package from worker: NOTE UNSAFE, does not check if
  something depends on what you are uninstalling. You will need to
  check this manually."
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

;; for these types of functions, you are always responsible for
;; yourself, clearing, setting, queuing, but always delegate others
;; work
(defn install
  "recursively installs id and delegates appropriately for
  dependencies"
  [transactor args-m]
  (let [{:keys [id need-to-queue installer]} args-m
	dependencies (:dependencies id)
	;; we do this to minimize the time spent in a transaction
	end-op (ref nil)
	set-end-op #(ref-set end-op %)
	op {:operation :install
	    :id id}
	do-queue (fn []
		   (doall (pmap (fn [f]
				  (f transactor))
				need-to-queue))
		   (dosync
		    (alter transactor
			   update-in
			   [:dependency-queue]
			   dissoc
			   op)))
	do-nothing (fn [])
	do-installer (fn []
		       (installer id transactor)
		       (dosync
			(alter transactor
			       update-in
			       [:running-tasks]
			       dissoc
			       op)))
	install-dependencies (fn []
			       ;; only add self to queue on first dependency
			       (let [f-id (first dependencies)]
				 (install transactor {:id f-id
						      :need-to-queue need-to-queue
						      :installer installer}))
			       (doall (pmap (fn [id]
					      (install transactor {:id id
								   :installer installer}))
					    (rest dependencies))))]
    (dosync
     (let [t @transactor
	   idx (:index t)
	   rt (:running-tasks t)
	   dq (:dependency-queue t)]
       (if (idx id)
	 (set-end-op do-queue)
	 (if (rt op)
	   (do
	     (alter transactor
		    update-in
		    [:dependency-queue op]
		    set/union
		    need-to-queue)
	     (set-end-op do-nothing))
	   (let [missing-dependencies (filter #(idx %) dependencies)]
	     ;; I could also check running-dependencies, but install
	     ;; already checks for this
	     (if (empty? missing-dependencies)
	       (do
		 (alter transactor
			assoc-in
			[:running-tasks]
			op
			:running)
		 (set-end-op (fn []
			       (do-installer)
			       (do-queue))))
	       (set-end-op install-dependencies)))))))
    (@end-op)
    nil))

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
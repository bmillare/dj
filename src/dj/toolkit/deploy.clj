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
;; must have a file 'package-index.clj' (described below), a 'repo'
;; directory, for a common place (but not limited to) to store
;; packages, and a 'tmp' directory, for jobs to safely, store
;; intermediates and results.

;; path/[package-index.clj, repo, tmp]

;; package - a folder containing files and a metadata file
;; (package.clj) containing the runtime dependencies, name, group,
;; version, and more (see below for full spec). Installing it should
;; only require copying the files and updating the package-index. The
;; name of the folder does not matter, only its contents.

;;; Package spec
;; {:name, :group, :version, :runtime-dependencies, :external-paths -
;; for meta packages that point to files already present on worker,
;; :methods - leads to another map, which has keys bound to specific
;; operations. The :executable key is the single package dir relative
;; path to a default executable}

;;; Package methods
;; In order to prepare for future functionality, the :methods key is
;; available in the package spec. We can add namespaced methods, which
;; is a map of whatever you want. The intention is to provide an
;; extensible bed for adding functionality.

;; package-id - an identifier that is 1-1 to a particular package

;; package-index.clj - each worker has a package index, which has a
;; package{name and group only} -> version -> install path, nested
;; map. This file is stored on the worker.

;; dependency - when a package needs to declare a dependency, it will
;; return a map with name, group, and version-classifier. The version
;; will be a type implements a version-accept? interface, which
;; accepts a version, and returns true if version is acceptable,
;; otherwise nil. The reason to do this is for security. There aren't
;; any forms that are evaluated, only reader loading of data types.

;; builder - a function that accepts a dependency and returns the
;; package path or fails and instead calls its continuation with the
;; dependency passed. This continuation exists via a closure.

;;; basic builder types:
;; -package cache, local or remote
;; -builder cache
;; -from sources
;; -custom recursive

;; -------------------------------------------------------------------

(defn durable-ref
  "returns a ref with a watcher that writes to file contents of ref as
it changes. Write file is considered a 'cache' in that it will try to
be up to date as much as possible, but may occasionaly be out of
date (dirty). The implementation will try to update the file as much
as possible without slowing down the ref. Also takes a default value,
where it will create a file if it doesn't exist, otherwise it will not
overwrite the value."
  ([file-path default-value]
     (let [^java.io.File f (tk/new-file file-path)]
       (when-not (.exists f)
	 (tk/poop f
		  (binding [*print-dup* true]
		    (prn-str default-value))))
       (durable-ref file-path)))
  ([file-path]
     (let [^java.io.File f (tk/new-file file-path)
	   writer-queue (agent nil)]
       ;; When the agent starts writing, the cache is marked clean

       ;; When the ref changes, a call to the writer is made. The cache
       ;; is dirty, no new calls to the writer is made while the cache
       ;; is dirty
       (let [dirty (ref false)
	     r (ref (load-file file-path))
	     clean (fn []
		     (dosync
		      (ref-set dirty false))
		     (tk/poop f
			      (binding [*print-dup* true]
				(prn-str @r))))]
	 (add-watch r :writer (fn [k r old-state state]
				(dosync
				 (when-not @dirty
				   (ref-set dirty true)
				   (send-off writer-queue
					     (fn [_]
					       (clean)))))))
	 r))))

;; The make-work-dir function is needed to build folders on a machine
;; that is unique within a parent folder.
(defn make-work-dir
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
		(Exception. "Cannot make work-dir, tried 5 times"))))]
    (f (range 5))))

(defn read-file [file]
  (let [data (tk/eat file)]
    (if (empty? data)
      nil
      (read-string data))))

;;; Worker functions

;;; Todo
;; halt, done?

(defn install
  "installs package (from package-dir) to worker (deploy-dir), copies
  files and updates package-index"
  [deploy-dir package-dir]
  (let [package-index-file (tk/relative-to
			    deploy-dir
			    "package-index.clj")
	package-index (read-file package-index-file)
	{:keys [name group version] :as pid} (read-file
					      (tk/relative-to
					       package-dir
					       "package.clj"))]
    ;; don't install if already installed but if package-index doesn't
    ;; exist, then install
    (if (if package-index
	  ((package-index {:name name :group group})
	   version)
	  nil)
      (throw (Exception. (str "Package "
			      (pr-str pid)
			      " already exists")))
      (let [install-dir (tk/relative-to deploy-dir "repo")
	    work-dir (make-work-dir install-dir)]
	;; copy files in directory
	(tk/cp-contents package-dir work-dir)
	;; update package-index
	(tk/poop package-index-file
		 (prn-str
		  (update-in (try package-index
				  (catch
				      java.io.FileNotFoundException e
				    {}))
			     [{:name name :group group}]
			     assoc
			     version (tk/get-path work-dir))))))))

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

;; In the future, for meta packages, uninstall should also call
;; uninstall methods within package so that they can clean up
;; externally installed files

(defn uninstall
  "removes package from worker"
  [deploy-dir pid]
  (let [{:keys [name group version]} pid
	package-index-file (tk/relative-to deploy-dir
					   "package-index.clj")
	package-index (or (read-file package-index-file)
			  (throw
			   (Exception.
			    "No package-index found")))
	package-path ((package-index
		       {:name name
			:group group})
		      version)]
    (if package-path
      (tk/rm
       (change-path deploy-dir
		    package-path))
      (throw (Exception. "Package not found")))
    ;; update package-index
    (tk/poop package-index-file
	     (prn-str
	      (update-in package-index
			 [{:name name :group group}]
			 dissoc version)))))

;; Get run to work,
(defprotocol IShIn
  (sh-in [dir sh-script] "run script in directory"))

(extend-type java.io.File
  IShIn
  (sh-in [dir sh-script]
	  (sh/sh "sh" "-c" sh-script :dir dir)))

(extend-type dj.toolkit.remote-file
  IShIn
  (sh-in [dir sh-script]
	  (let [{:keys [path username server port]} dir]
	    (sh/sh "ssh" (str username "@" server) "-p" (str port)
		   "sh" "-c"
		   (str "'cd " path "; " sh-script "'")))))

(defn pass
  "pass will pass the package-index and package metadata to the
  function f and then call f"
  [deploy-dir pid f]
  (let [{:keys [name group version]} pid
	package-index-file (tk/relative-to deploy-dir
					   "package-index.clj")
	package-index (or (read-file package-index-file)
			  (throw
			   (Exception.
			    "No package-index found")))
	package-path ((package-index
		       {:name name
			:group group})
		      version)
	package-data (read-file
		      (change-path
		       deploy-dir
		       (tk/str-path
			package-path
			"package.clj")))]
    (f package-index package-data)))

(defn run
  "if package is runnable, then will start job, returns
   job-reference"
  [deploy-dir pid arg-line]
  (pass deploy-dir pid
	(fn [package-index package-data]
	  (let [{:keys [executable]} (:methods package-data)]
	    (sh-in (tk/relative-to deploy-dir "tmp")
		   (str executable " " arg-line))))))

;; Problem, since there is data about version, group, and name in the
;; package.clj and in the index, the information needs to be
;; synced. There needs to be an update function, where update will
;; look at the current data in package.clj, and update the index. So
;; this means, go into package directory, then read package.clj, then
;; update values.

(defn init-deploy-dir [dir]
  (tk/mkdir dir)
  (tk/mkdir (tk/relative-to dir "repo"))
  (tk/mkdir (tk/relative-to dir "tmp"))
  (tk/poop (tk/relative-to dir "package-index.clj")
	   "{}\n"))

(defn init-package-dir
  "metadata is a map, be sure to include at least name, group, and
  version. Note you can also include
 :runtime-dependencies, :external-paths, and :executable."
  [dir metadata]
  (tk/mkdir dir)
  (tk/poop dir
	   (prn-str metadata)))

(defprotocol Iversion-accept?
  (version-accept? [this version]))

(extend-type java.lang.String
  Iversion-accept?
  (version-accept? [this version]
		   (= this version)))

(defn local-package-cache-builder-factory [package-idx-path fail]
  (fn [{:keys [name group version] :as dependency}]
    (let [version-map ((read-file (tk/new-file package-idx-path))
		       {:name name
			:group group})]
      (if-let [accept-v (some (fn [v]
				(if (version-accept? version v)
				  v
				  nil))
			      (keys version-map))]
	(version-map accept-v)
	(fail dependency)))))
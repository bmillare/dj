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

;; builder - a function that accepts a install-directory and installs
;; the package files in that directory.

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

(defn read-file [file]
  (let [data (tk/eat file)]
    (if (empty? data)
      nil
      (read-string data))))

;;; Worker functions

;;; Todo
;; halt, done?

(defn update-index-data
  "update-index helper function, returns new package-index with pid
  from package-metadata installed"
  [package-index package-install-path package-metadata]
  (let [{:keys [name group version]} package-metadata]
    (prn-str
     (update-in package-index
		[{:name name :group group}]
		assoc
		version package-install-path))))

(defn update-index
  "updates package-index in deploy-dir from data in package-dir"
  [deploy-dir package-dir]
  (let [package-index-file (tk/relative-to
			    deploy-dir
			    "package-index.clj")
	package-index (try (read-file package-index-file)
			   (catch Exception e
			     {}))
	package-metadata (read-file
			  (tk/relative-to
			   package-dir
			   "package.clj"))
	{:keys [name group version]} package-metadata
	;; don't install if already installed but if package-index doesn't
	;; exist, then install
	write-idx? (if (empty? package-index)
		     true
		     (let [versions (package-index {:name name :group group})]
		       (if versions
			 (if (versions version)
			   (throw (Exception. (str "Package "
						   (pr-str {:name name
							    :group group
							    :version version})
						   " already exists")))
			   true)
			 true)))]
    (when write-idx?
      (tk/poop package-index-file
	       (update-index-data package-index
				  package-dir
				  package-metadata)))))

(defn cp-install
  "installs package (from package-dir) to worker (deploy-dir), copies
  files and updates package-index"
  [deploy-dir package-dir]
  (let [install-dir (tk/relative-to deploy-dir "repo")
	work-dir (make-unique-dir install-dir)]
    ;; copy files in directory
    (tk/cp-contents package-dir work-dir)
    ;; update package-index
    (update-index deploy-dir package-dir)))

(defn build-install
  "installs package (from package-dir) to worker (deploy-dir),
calls builder with package-dir and then updates package-index"
  [deploy-dir builder]
  (let [install-dir (tk/relative-to deploy-dir "repo")
	package-dir (make-unique-dir install-dir)]
    ;; build files in directory
    (builder package-dir)
    ;; update package-index
    (update-index deploy-dir package-dir)))

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

(defn pid-from-dir [f]
  (let [{:keys [name group version]} (read-file
				      (tk/relative-to f
						      "package.clj"))]
    {:name name
     :group group
     :version version}))

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
		    (tk/get-path package-path)))
      (throw (Exception. "Package not found")))
    ;; update package-index
    (tk/poop package-index-file
	     (prn-str
	      (update-in package-index
			 [{:name name :group group}]
			 dissoc version)))))

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
(ns dj.toolkit.deploy
  (:require [clojure.set :as set])
  (:require [dj.toolkit :as tk]))

;; deploy - A library for building, installing, and running software
;; generically on different types of systems.

;; Model:

;; worker - computer, something capable of storing files and
;; performing work

;; worker-ref - a reference to the machine, can be used to access
;; package-index, work load, submit, etc. Using a remote file is
;; insufficient since we need to send commands, not just files.

;; package - a folder containing files and a metadata file containing
;; the runtime dependencies. Installing it should only require copying
;; the files and updating the package-index. The name of the folder
;; does not matter, only its contents.

;; package-id - an identifier that is 1-1 to a particular package

;; package-index - each worker has a package index, which has a
;; package -> install path, map. This file is stored on the worker.

;; dependency - when a package needs to declare a dependency, it will
;; return this type. This type implements a pkg-accept? interface,
;; which accepts a pid, and returns true if pid is acceptable,
;; otherwise nil.

;; builder - a function that accepts no arguments. It builds a package
;; and returns its path

;; builder-factory - accepts a pid and returns a respective builder or
;; fails. Since we typically will be making a request from a tree of
;; builder-factories, and builders can succeed or fail, continuation
;; passing style might be useful here. This way we don't have to
;; interpret the return value.

;; basic builder-factories:
;; -package cache, local or remote
;; -builder cache
;; -from sources
;; -custom recursive

;; -------------------------------------------------------------------

;; The work-dir function is needed to build folders on a machine that
;; is unique within a parent folder.
(defn work-dir
  "will attempt to make a directory of any name with parent,
  parent-dir"
  [parent-dir]
  (letfn [(f [attempt]
	     (if attempt
	       (let [id (. System (nanoTime))]
		 (try (tk/mkdir (tk/relative-to parent-dir (str id)))
		      (catch Exception e
			(f (next attempt)))))
	       (throw (Exception. "Cannot make work-dir, tried 5 times"))))]
    (f (range 5))))

;; Worker-refs
(defrecord ssh-worker-ref [path username address port])

(defrecord local-worker-ref [path])

(defn ->pid [name group version]
  {:name name
   :group group
   :version version})

(defrecord package-metadata [pid contents-paths dependencies])
;; pid: a package-id
;; contents-paths: a vector of paths
;; dependencies: a vector of runtime dependencies

(defn local-package-cache-builder-factory-factory [cache-path]
  (fn [pid]
    (if )))

;; -------------------------------------------------------------------

;; builds are defined to be blocking (waiting, syncrhonous), running
;; implies asyncrhonous.

;; There is no such thing as an executor object. There are only
;; library functions and different queuing policies.

;; Work knows its dependencies, work will need to provide dependency
;; information so that functions will allocate work on resource that
;; is appropriate.

;; A simple dispatch policy would be to run the work on a resource
;; that can support it, and is the most free.

;; All types of work will need to support basic functionality in order
;; to interface with executors, thus I have the following protocol.

(defprotocol Iwork
  (dependencies-satisfied? [work resource]
			   "returns true if the resource can provide
  all dependencies required by work")
  (allocate [work resource ok err]
	    "allocate must create additional runtime dependencies for
	    work on resource, passes handle to ok on success, !!
	    Haven't decided what to pass to err, for now just call it")
  (run [work resource handle ok err]
       "executes job on resource, calls ok on success, err on fail")
  (halt [work resource handle ok err]
	"stops job on resource, calls ok on success, err on fail")
  (result [work resource handle ok err]
	  "returns result of job on resource, calls ok on success, err
	  on fail")
  (deallocate [work resource handle ok err]
	      "deletes allocated resources of job on resource, calls
	  ok on success, err on fail"))

;; The executor interface is just a simple wrapper over the Iwork
;; protocol. The executor handles is simply the resource the work is
;; running on, the work itself for type information, and the handle
;; returned by allocate.

(defn err []
  (println "Error!"))

(defn submit
  ([resources work]
     ;; current default is just the first resource, this is obviously
     ;; bad, we will later add a more intelligent resource chooser
     (submit first resources work))
  ([r-fn resources work]
     (let [r (r-fn resources)]
       (allocate r work
		 (fn [handle]
		   [r work handle])
		 err))))

(defn start [[resource work handle]]
  (run work resource handle identity err))

(defn stop [[resource work handle]]
  (halt work resource handle identity err))

(defn return [[resource work handle]]
  (result work resource handle identity err))

(defn delete [[resource work handle]]
  (deallocate work resource handle identity err))

;; Now we only need to define the types of work we will use. For HTK,
;; the common work type is the torque script.

;; First, we need to define some helper functions for all script like
;; work.
(defn greatest-script-id [resource]
  (let [;; job-ids is all the ids of the storage directory
	job-ids (map #(Integer/parseInt (tk/get-name %))
		     (filter #(re-matches #"\d+" (tk/get-name %))
			     (tk/ls (:storage-directory resource))))]
    (if (empty? job-ids)
      0
      (apply max job-ids))))

;; To minimize contention and communication between the server and
;; client, I will cache the latest folder id on first call, and
;; subsequent calls will synchronize on local cache only, I have to
;; assume there is only one accessor to the storage directory at all
;; times
(let [resource-state (ref {})]
  (defn new-script-dir
    "returns a new workspace file"
    [resource]
    (let [{:keys [storage-directory]} resource]
      (dosync
       (if-let [path-a (@resource-state resource)]
	 (tk/relative-to storage-directory
			 (str ((alter resource-state update-in [resource] inc) resource)))
	 (tk/relative-to storage-directory
			 (str ((alter resource-state assoc-in [resource] (greatest-script-id resource)) resource))))))))
;; torque-work contains all the dependency data
;; runtime-dependencies: a vector of runtime dependency classifiers

;; This is everything that needs to already be running at the time of
;; execution

;; software-dependencies: a map of aliases to satisfies functions

;; These are the software files that are on the system

;; copy-files:

;; These are the files that are on the dispatcher that need to be copied
;; over

;; make-executables:

;; This is a function, when given the script-dir and the alias-path
;; map, will make files that need to be written over

;; !! its possible we need to generate files from a different program
;; !! other than java, and it may be in binary form
(defrecord torque-work [runtime-dependencies software-dependencies copy-files make-executables]
  Iwork
  (dependencies-satisfied? [work resource]
			   (let [{:keys [packages runtimes storage-directory]} resource]
			     (and (not-every? #(empty? (set/select %
								   runtimes))
					      runtime-dependencies)
				  (not-every? #(empty? (set/select %
								   packages))
					      (vals software-dependencies)))))
  (allocate [work resource ok err]
	    (let [{:keys [packages runtimes storage-directory]} resource
		  ;; want aliases -> path
		  path-map (reduce (fn [m a]
				     (let [r (first (set/select (software-dependencies a)
								packages))]
				       (if r
					 m
					 (assoc m a (:path r)))))
				   {}
				   (keys software-dependencies))
		  script-dir (new-script-dir resource)
		  exes (make-executables path-map script-dir)]
	      ;; copy files (map, file location local, file
	      ;; location relative remote)
	      (doseq [[txt path] exes]
		(tk/poop (tk/relative-to script-dir path)
			 txt))
	      (doseq [[f path] copy-files]
		(tk/cp f (tk/relative-to script-dir path)))))
  (run [work resource handle ok err]
       "executes job on resource, calls ok on success, err on fail")
  (halt [work resource handle ok err]
	"stops job on resource, calls ok on success, err on fail")
  (result [work resource handle ok err]
	  "returns result of job on resource, calls ok on success, err
	  on fail")
  (deallocate [work resource handle ok err]
	      "deletes allocated resources of job on resource, calls
	  ok on success, err on fail"))


;; Sample resource map
;;
#_ {:packages {{:name "carp" :group "cardiosolv"} [{:version "1.5.0" :path "/path to place"}]
	       }
    :runtimes {{:name "torque"} nil}
    :storage-directory (tk/new-file "/path/to/place")}

;; Can add later resume, pause, done?

#_ (do
     (new-script-dir {:packages #{{:name "carp" :group "cardiosolv" :version "1.5.0" :path "/foo/bar"}}
		      :runtimes #{{:name "torque"}}
		      :storage-directory (tk/new-file "/home/hara/tmp")})
     (first nil)
     (clojure.set/select)
     (work-dir (tk/new-remote-file "/home/bmillare/tmp" "bmillare" "boh.icm.jhu.edu" 22))

     (allocate work resource identity err)
     )
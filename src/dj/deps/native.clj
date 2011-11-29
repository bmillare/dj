(ns dj.deps.native
  (:require [dj.cli])
  (:require [dj.deps.maven])
  (:use [dj.deps.core])
  (:use [dj.core :only [system-root]])
  (:use [dj.toolkit :only [new-file ls unjar]]))

(defrecord native-dependency [name version group pom-cache])

(defn make-native-dependency [name version group]
  (native-dependency. name version group (atom nil)))

(defmethod parse :native-dependency [obj & [_]]
	   (let [id (first obj)
		 version (second obj)]
	     (make-native-dependency (name id)
				     version
				     (or (namespace id)
					 (name id)))))

;; dj assumes that the structure of the native jar is a particular
;; format, which is used by clojars. The jars that contain native
;; components have 3 directories, lib, which has jars, META-INF,
;; contains the manifest, and native, which contains platform folders,
;; in each platform folder, there is the architecture, and in each
;; architecture, is the library files. Each library file must have the
;; prefix "lib" and then the library name, and then the platform
;; dependent suffix. Also, currently, there needs to be the
;; corresponding pom file in the maven repository of the same
;; folder. I'm not sure how to fix this at the moment. I'm basically
;; using the maven behavior for dependency management and the native
;; repository for extracting the files to. Probably the pom file
;; should be associated with the native repo items.

(defn get-native!
  "check if native jar exists in local native repository, else,
  downloads and unjars jar in native directory, returns lib native files"
  [dependency]
  ;; property for
  ;; os.name  platforms
  ;; os.arch  architectures
  (let [platforms {"Linux" "linux"
		   "Mac OS X" "macosx"
		   "Windows" "windows"
		   "SunOS" "solaris"}
	architectures {"amd64" "x86_64"
		       "x86_64" "x86_64"
		       "x86" "x86"
		       "i386" "x86"
		       "arm" "arm"
		       "sparc" "sparc"}
	install-dir (new-file system-root
			      "usr/native"
			      (str (dj.deps.maven/relative-directory dependency)
				   (:name dependency) "-" (:version dependency)))]
    (when-not (.exists install-dir)
      (println install-dir)
      (unjar (dj.toolkit/log (new-file (dj.deps.maven/obtain-normal-maven dependency)))
	     install-dir))
    [(seq (ls (new-file install-dir "lib")))
     (let [native-dir (new-file install-dir
				"native"
				(platforms (System/getProperty "os.name"))
				(architectures (System/getProperty "os.arch")))]
       #_ (concat [native-dir] (ls native-dir))
       ;; BUG: some programs want dir, others want the files, don't know how to customize resolving
       (list native-dir))]))

(extend native-dependency
  ADependency
  {:obtain (fn [dependency {:keys [offline]}]
	     (if (dj.deps.maven/is-snapshot? dependency)
	       (throw (java.lang.Exception. "snapshot native dependencies not implemented"))
	       (get-native! dependency)))
   :depends-on #(dj.deps.maven/pass-pom-data % dj.deps.maven/pom-extract-dependencies)
   :load-type (fn [d] :native)
   :exclusions #(dj.deps.maven/pass-pom-data % dj.deps.maven/pom-extract-exclusions)})


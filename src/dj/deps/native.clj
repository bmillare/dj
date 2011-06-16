(ns dj.deps.native
  (:require [dj.io])
  (:require [dj.cli])
  (:require [dj.deps.maven])
  (:use [dj.deps.core])
  (:use [dj.core :only [system-root]]))

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
	install-dir (dj.io/file system-root
			       "./usr/native/"
			       (str (dj.deps.maven/relative-directory dependency)
				    (:name dependency) "-" (:version dependency)))]
    (when-not (.exists install-dir)
      (dj.io/unjar (dj.io/file (dj.deps.maven/obtain-normal-maven dependency))
		   install-dir))
    [(seq (.listFiles (dj.io/file install-dir "./lib/")))
     (let [native-dir (dj.io/file install-dir
				  "./native/"
				  (platforms (System/getProperty "os.name"))
				  (architectures (System/getProperty "os.arch")))]
       #_ (concat [native-dir] (.listFiles native-dir))
       ;; BUG: some programs want dir, others want the files, don't know how to customize resolving
       (list native-dir))]))

(extend native-dependency
  ADependency
  {:obtain (fn [dependency {:keys [offline]}]
	     (if (dj.deps.maven/is-snapshot? dependency)
	       (throw (java.lang.Exception. "snapshot native dependencies not implemented"))
	       (get-native! dependency)))
   :depends-on dj.deps.maven/maven-depends-on
   :load-type (fn [d] :native)
   :exclusions dj.deps.maven/maven-exclusions})


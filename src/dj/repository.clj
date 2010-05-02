(ns dj.repository
  (:import [java.io File FileNotFoundException IOException])
  (:import [java.net URL])
  (:use [dj.net :only [wget!]])
  (:use [dj.core :only [system-root]]))

(def repository-urls ["http://repo1.maven.org/maven2"
		      "http://clojars.org/repo/"])

(def local-repository-path (File. system-root "./usr/maven/"))

(defn delete-recursive [#^File f]
  "inclusive delete recursively"
  (if (.isDirectory f)
    (do (doseq [child (.listFiles f)]
	  (delete-recursive child))
	(.delete f))
    (.delete f)))

(defn get-dependency-path-prefix [dependency]
  "takes dependency form [group-id/artifact-id \"version\"] and
  returns String path prefix"
  (let [artifact-id (name (dependency 0))
	group-id (or (.replaceAll (namespace (dependency 0))
				  "\\."
				  "/")
		     artifact-id)
	version (dependency 1)]
    (str group-id "/"
	 artifact-id "/"
	 version "/"
	 artifact-id "-" version)))

(defn get-dependency-path [dependency file-extension repository-path]
  "takes dependency and file-extension returns File path to local copy in repository"
  (File. repository-path
	 (str (get-dependency-path-prefix dependency)
	      file-extension)))

(defn get-dependency-URL [dependency file-extension repository-url]
  "takes dependency, file-extension, and repository, and returns String URL to the file"
  (str (if (= \/ (last repository-url))
	 repository-url
	 (str repository-url "/"))
       (get-dependency-path-prefix dependency)
       file-extension))

(defn make-tmp-folder! [#^File directory]
  "makes tmp folder with unique name in directory, returns path to folder"
  (if (.exists directory)
    (loop []
      (let [file-list (seq (.listFiles directory))
	    largest-count (if file-list
			    (apply max (for [f file-list] (Integer/parseInt (.getName f))))
			    0)
	    tmp-folder-path (File. directory (str (inc largest-count)))]
	(if (.mkdir tmp-folder-path)
	  tmp-folder-path
	  (if (.exists tmp-folder-path)
	    (recur)
	    (throw (IOException. (.getCanonicalPath tmp-folder-path)))))))
    (throw (FileNotFoundException. (.getCanonicalPath directory)))))

(defn make-directory! [#^File directory]
  "makes directory or directories if it doesn't exist already, returns path to folder"
  (when-not (.exists directory)
    (when-not (.mkdirs directory)
      (throw (IOException. (str "cannot create directory "
				(.getCanonicalPath directory)
				" please clean up")))))
  directory)

(defn download-dependency! [dependency]
  "downloads files for a single dependency if not in local repository, returns install folder path"
  (let [install-jar (get-dependency-path dependency ".jar" local-repository-path)
	install-pom (get-dependency-path dependency ".pom" local-repository-path)
	install-folder (.getParentFile install-jar)]
   (letfn [(wget-from!
	    [repositories]
	    "attempt to get dependency from repositories in order,
            downloads files to temporary directory before moving, does clean up after"
	    (let [url-jar (get-dependency-URL dependency ".jar" (first repositories))
		  url-pom (get-dependency-URL dependency ".pom" (first repositories))
		  tmp-folder (make-tmp-folder! (make-directory! (File. system-root "tmp/repository")))]
	      (try
	       (let [downloaded-pom (wget! url-pom tmp-folder)
		     downloaded-jar (wget! url-jar tmp-folder)]
		 (make-directory! install-folder)
		 (.renameTo downloaded-jar install-jar)
		 (.renameTo downloaded-pom install-pom)
		 install-folder)
	       (catch FileNotFoundException e
		 (if (next repositories)
		   (wget-from! (next repositories))
		   (throw (FileNotFoundException. (.getFile (URL. url-jar))))))
	       (finally (delete-recursive tmp-folder)))))]
     (if (and (.exists install-jar)
	      (.exists install-pom))
       install-folder
       (wget-from! repository-urls)))))

(defn testo []
  ;;(wget! "http://build.clojure.org/releases/org/clojure/clojure/1.1.0/clojure-1.1.0.pom" (File. "/home/hara/.downloads/"))
  ;;(wget! "http://clojure.org" (File. "/home/hara/.downloads/"))
  (download-dependency! ['org.clojure/clojure-contrib "1.1.0"])
  ;;(get-dependency-path ['org.clojure/clojure "1.1.0"] ".jar" local-repository-path)

  )
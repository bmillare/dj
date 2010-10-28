(ns dj.deps.maven
  (:require [dj.io])
  (:require [clojure.xml])
  (:require [dj.net])
  (:use [dj.deps.core]))

(defn repository-url
  "makes given repository address (string) ensures it is well formed"
  [repository]
  (if (.endsWith repository "/")
    repository
    (str repository "/")))

;; later add local repositories, grabbed from pom file
(def repository-urls (map repository-url ["http://repo1.maven.org/maven2"
					  "http://clojars.org/repo/"
					  "http://alxa.sourceforge.net/m2"]))

(defrecord maven-dependency [name version group pom-cache])

(defn make-maven-dependency [name version group]
  (maven-dependency. name version group (atom nil)))

(defn condense-xml
  "given output from clojure.xml/parse, returns same tree but
   condensed to just the tag and content"
  [{:keys [tag content] :as xml-map-entry}]
  (if (vector? content)
    (if (:tag (first content))
      {tag (map condense-xml content)}
      {tag content})
    (if (:tag content)
      {tag (condense-xml content)}
      {tag content})))

(defmethod parse (class []) [obj & [_]]
	   (let [id (first obj)
		 version (second obj)]
	     (make-maven-dependency (name id)
				version
				(or (namespace id)
				    (name id)))))

(defn find-map-entry [m k]
  (k (first (filter k m))))

(defn is-snapshot?
  [dependency]
  (.contains (:version dependency) "SNAPSHOT"))

(defn relative-directory
  "takes dependency map and returns String path prefix"
  [{:keys [name version group]}]
  (str (.replaceAll group "\\." "/") "/"
       name "/"
       version "/"))

(defn latest-prefix
  [mvn-metadata]
  (let [ffilter (fn [x k] (k (first (filter k x))))
	metadata (:metadata mvn-metadata)
	id (-> metadata
	       (ffilter :artifactId)
	       first)
	snapshot-version-prefix (-> metadata
				    (ffilter :version)
				    first
				    (#(re-matches #"(.+)SNAPSHOT" %))
				    second)
	versioning-snapshot (-> metadata
				(ffilter :versioning)
				(ffilter :snapshot))
	timestamp (-> versioning-snapshot
		      (ffilter :timestamp)
		      first)
	buildNumber (-> versioning-snapshot
			(ffilter :buildNumber)
			first)]
    (str id "-" snapshot-version-prefix timestamp "-" buildNumber)))

(defn obtain-normal-maven
  [dependency]
  (let [directory-file-prefix (str (relative-directory dependency)
				   (:name dependency) "-" (:version dependency))
	local-jar (dj.io/file
		   repositories-directory
		   "maven"
		   (str directory-file-prefix ".jar"))
	local-pom (dj.io/file
		   repositories-directory
		   "maven"
		   (str directory-file-prefix ".pom"))]
    (if (and (.exists local-pom)
	     (.exists local-jar))
      local-jar
      (dj.io/with-tmp-directory tmp-folder (dj.io/file dj.core/system-root "tmp/repository")
	(letfn [(download-jar-pom!
		 [repositories]
		 "attempt to get dependency from repositories in order,
                                   downloads files to temporary (run within a with-tmp-directory)
                                   directory before moving, does clean
                                   up after"
		 (try
		   (let [downloaded-pom (dj.net/wget! (str (first repositories) directory-file-prefix ".pom")
						      tmp-folder)
			 downloaded-jar (dj.net/wget! (str (first repositories) directory-file-prefix ".jar")
						      tmp-folder)]
		     ;; side effects, order important
		     (dj.io/make-directory! (.getParentFile local-pom))
		     ;; move files to local repository
		     (.renameTo downloaded-jar local-jar)
		     (.renameTo downloaded-pom local-pom)
		     local-jar)
		   (catch java.io.FileNotFoundException e
		     (if (next repositories)
		       (download-jar-pom! (next repositories))
		       (throw (java.io.FileNotFoundException. (str "Can't find "
								   directory-file-prefix
								   ".pom from any remote repository")))))))]
	  (download-jar-pom! repository-urls))))))

;; base locations
;;  url
;;  repositories-directory

;; repository -> "maven" "native"

;; relative-directory, determined from dependency

;; location + relative-directory
;;  remote-directory
;;  local-directory

;; file-prefix, determined from dependency
;; file-suffix, ".pom" ".xml"

(defn pom-file
  "returns path to pom file for d, accounts for possibility of being a
snapshot"
  [d]
  (let [let-relative-directory (relative-directory d)
	f (dj.io/file repositories-directory "maven" (str let-relative-directory "maven-metadata.xml"))]
    (if (.exists f)
      (dj.io/file repositories-directory "maven" (str let-relative-directory (latest-prefix (condense-xml (clojure.xml/parse f))) ".pom"))
      (dj.io/file repositories-directory "maven" (str let-relative-directory (:name d) "-" (:version d) ".pom")))))

(defn obtain-snapshot-maven
  [dependency offline?]
  (let [let-relative-directory (relative-directory dependency)
	local-maven-metadata-file (dj.io/file repositories-directory "maven" (str let-relative-directory "maven-metadata.xml"))]
    (if offline?
	(if (.exists local-maven-metadata-file)
	  ;; return local jar file based on the xml file
	  (dj.io/file repositories-directory
		      "maven"
		      let-relative-directory
		      (str (latest-prefix (condense-xml (clojure.xml/parse local-maven-metadata-file)))
			   ".jar"))
	  (throw (java.io.FileNotFoundException. (str "Can't find "
						      local-maven-metadata-file
						      ".pom from local repository"))))
	;; obtain and (return xml-snapshot or return regular-snapshot)
	(loop [urls repository-urls]
	  (if urls
	    (let [remote-directory (str (first urls) let-relative-directory)]
	      (if (dj.net/exists? remote-directory)
		(let [remote-xml-file (str remote-directory "maven-metadata.xml")]
		  (if (dj.net/exists? remote-xml-file)
		    (dj.io/with-tmp-directory tmp-folder (dj.io/file dj.core/system-root "tmp/repository")
		      (let [downloaded-xml (dj.net/wget! remote-xml-file tmp-folder)
			    file-prefix (latest-prefix (condense-xml (clojure.xml/parse (dj.io/file tmp-folder "maven-metadata.xml"))))
			    downloaded-pom (dj.net/wget! (str remote-directory file-prefix ".pom") tmp-folder)
			    downloaded-jar (dj.net/wget! (str remote-directory file-prefix ".jar") tmp-folder)
			    local-jar (dj.io/file repositories-directory "maven" (str let-relative-directory file-prefix ".jar"))
			    local-xml (dj.io/file repositories-directory "maven" (str let-relative-directory "maven-metadata" ".xml"))
			    local-pom (dj.io/file repositories-directory "maven" (str let-relative-directory file-prefix ".pom"))]
			;; side effects, order important
			(dj.io/make-directory! (.getParentFile local-pom))
			;; move files to local repository
			(.renameTo downloaded-jar local-jar)
			(.renameTo downloaded-pom local-pom)
			(.renameTo downloaded-xml local-xml)
			local-jar))
		    (dj.io/with-tmp-directory tmp-folder (dj.io/file dj.core/system-root "tmp/repository")
		      (let [file-prefix (str (:name dependency) "-" (:version dependency))
			    downloaded-pom (dj.net/wget! (str remote-directory file-prefix ".pom") tmp-folder)
			    downloaded-jar (dj.net/wget! (str remote-directory file-prefix ".jar") tmp-folder)
			    local-jar (dj.io/file repositories-directory "maven" (str let-relative-directory file-prefix ".jar"))
			    local-pom (dj.io/file repositories-directory "maven" (str let-relative-directory file-prefix ".pom"))]
			;; side effects, order important
			(dj.io/make-directory! (.getParentFile local-pom))
			;; move files to local repository
			(.renameTo downloaded-jar local-jar)
			(.renameTo downloaded-pom local-pom)
			local-jar))))
		(recur (next urls))))
	    (throw (java.io.FileNotFoundException. (str "Can't find "
							(relative-directory dependency) (:name dependency) "-" (:version dependency) ".pom"
							" from any remote repository"))))))))

"downloads files for a single dependency if not in local repository,
 returns file path snapshots always get a fresh copy unless in offline
 mode"

(extend maven-dependency
  ADependency
  {:obtain (fn [dependency {:keys [offline?]}]
	     (if (is-snapshot? dependency)
	       (obtain-snapshot-maven dependency offline?)
	       (obtain-normal-maven dependency)))
   :depends-on (fn [d]
		 (let [pom-cache (:pom-cache d)
		       project-data (:project (if @pom-cache
						@pom-cache
						(reset! pom-cache (-> (pom-file d)
								      clojure.xml/parse
								      condense-xml))))]
		   (for [{d :dependency} (find-map-entry project-data :dependencies)
			 :let [d-data (loop [c d
					     name-version-group {}]
					(if c
					  (let [x (first c)]
					    (case (first (keys x))
						  :groupId (recur (next c) (assoc name-version-group :group (first (:groupId x))))
						  :artifactId (recur (next c) (assoc name-version-group :name (first (:artifactId x))))
						  :version (recur (next c) (assoc name-version-group :version (first (:version x))))
						  :scope nil))
					  name-version-group))]
			 :when d-data]
		     (make-maven-dependency (:name d-data) (:version d-data) (:group d-data)))))
   :load-type (fn [d] :jar)
   :exclusions (fn [d]
		 (let [pom-cache (:pom-cache d)
		       project-data (:project (if @pom-cache
						@pom-cache
						(reset! pom-cache (-> (pom-file d)
								      clojure.xml/parse
								      condense-xml))))]
		   (seq (concat (for [{dependency :dependency} (find-map-entry project-data :dependencies)
				      :let [y (seq (concat (for [{e :exclusion} (find-map-entry dependency :exclusions)
						     :let [x (let [e-data (loop [c e
										 name-group {}]
									    (if c
									      (let [x (first c)]
										(case (first (keys x))
										      :groupId (recur (next c) (assoc name-group :group (first (:groupId x))))
										      :artifactId (recur (next c) (assoc name-group :name (first (:artifactId x))))))
									      name-group))]
							       (fn [d] (and (= (:group d) (:group e-data))
									    (= (:name d) (:name e-data)))))]
						     :when x]
							     x)))]
				      :when y]
				  y)))))})

;; TODO
;; write obtain
					;
;; as a policy, if unsure about being in core, have it such that it
;; must eventually be promoted to core

;; repository protocol.  algorithm is to check for dep files in local
;; repo, if not there, then download from online repos. Need function
;; that determines remote location path, and a function that
;; determines local path.

;; problem: snapshot versions sometimes have a file that tells you
;; what file to use. Need to download/parse before downloading
;; others. If this is implemented in the caller, they need to know
;; where the repositories are at.

;; solution must contain ability to search for xml file, if not exist,
;; search for file titled SNAPSHOT, otherwise did not find in repo,
;; and do this for all repos. This applies to SNAPSHOT versions, which
;; is a lein/maven concept

;; if the repo function delegates this to another function, the repo
;; locations would need to be passsed, which is different than the
;; usual case, where the functions only return file suffix. The
;; download-dependency function then does the actual downloading.

;; SNAPSHOT downloaders need to do downloading, while normal suffix
;; generators do not. To make general, the delegator would need to do
;; the actual downloading.

;; perhaps best strategy for writing general code is to make only
;; general almost psuedo code like functions that delegate to
;; multimethods and protocols. Build a small toolkit of helper
;; functions, and allow the delegated methods to easily call such
;; helper functions.

;; again components: general-english-function,
;; delegating-multimethods, helper-functions that are called by
;; delegatees

;; plan of action therefore is to start building downloaders for
;; normal packages and also snapshot packages while building up the
;; helper-toolkit as needed.


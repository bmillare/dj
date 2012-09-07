(in-ns 'dj.io)

(defn file
  "returns a new java.io.File with args as files or str-paths"
  ^java.io.File
  [& paths]
  (java.io.File. ^String (apply dj/str-path (map (fn [p]
						   (if (= (type p)
							  java.io.File)
						     (.getPath ^java.io.File p)
						     p))
						 paths))))

(defn absolute-path? [str-path]
  (= (first str-path)
	 \/))

(defn sh-exception [result]
  (if (= (:exit result) 0)
    (:out result)
    (throw (Exception. ^java.lang.String (:err result)))))

(defn- shify [args]
  (apply str (interpose " " args)))

(defn ssh [username server port sh-code & args]
  (apply sh/sh
	 "ssh" (str username "@" server) "-p" (str port)
	 "sh" "-c"
	 (str "'" sh-code "'")
	 args))

(extend-type java.io.File
  Ieat
  (eat [this] (slurp this))
  Ipoop
  (poop ([this txt append]
	   ;; not portable
	   (if append
	     (sh-exception (sh/sh "sh" "-c" (str "cat - >> " (.getPath this)) :in txt))
	     (spit this txt)))
	([this txt]
	   (spit this txt)))
  Imkdir
  (mkdir [this] (if (.mkdirs this)
		  this
		  (throw (Exception. (str "Could not make directory " (.getCanonicalPath this))))))
  Iget-name
  (get-name [f]
	    (let [n (.getName f)]
	      (if (empty? n)
		nil
		n)))
  Ils
  (ls [dest]
      (seq (.listFiles dest)))
  Irm
  (rm [dest]
      (when (exists? dest)
	(when (.isDirectory dest)
	 (doseq [f (ls dest)]
	   (rm f)))
	(.delete dest)))
  Imv
  (mv [target ^java.io.File dest]
      (if (.isDirectory dest)
	(.renameTo target (file dest (.getName target)))
	(.renameTo target dest)))
  Irelative-to
  (relative-to [folder path]
	       (file folder path))
  Iparent
  (parent [f]
	  (.getParentFile f))
  Iget-path
  (get-path [f]
	    (.getPath f))
  Iexists
  (exists? [f]
	   (.exists f)))

(defrecord remote-file [path username server port]
  Ipoop
  (poop [dest txt append]
	(if append
	  (ssh username server port (str "cat - >> " path) :in txt)
	  (ssh username server port (str "cat - > " path) :in txt)))
  (poop [dest txt]
	(ssh username server port (str "cat - > " path) :in txt))
  Ieat
  (eat [dest]
       (let [result (ssh username server port (shify ["cat" path]))]
	 (if (zero? (:exit result))
	   (:out result)
	   (throw (Exception. ^java.lang.String (:err result))))))
  Imkdir
  (mkdir [dest]
	 (if (zero? (:exit (ssh username server port (str "mkdir -p " path))))
	   dest
	   (throw (Exception. (str "Could not make remote directory " path)))))
  Iget-name
  (get-name [f]
	    (last (.split ^String (:path f) "/")))
  Ils
  (ls [dest]
      (map #(remote-file. (str path "/" %) username server port)
	   (let [ls-str (:out (ssh username server port (shify ["ls" path])))]
	     (if (empty? ls-str)
	       nil
	       (.split ^String ls-str "\n")))))
  Irm
  (rm [target] (sh-exception
		(ssh username server port (shify ["rm" "-rf" path]))))
  Irelative-to
  (relative-to [folder path]
	       (remote-file. (dj/str-path (:path folder) path)
			     username server port))
  Imv
  (mv [target dest]
      (ssh username server port (shify ["mv" path dest])))
  Iparent
  (parent [f]
	  (assoc f
	    :path
	    (apply str (if (= (first (:path f))
			      \/)
			 "/"
			 "")
		   (interpose "/" (filter #(not (empty? %)) (drop-last (.split ^String (:path f) "/")))))))
  Iget-path
  (get-path [f]
	    path)
  Iexists
  (exists? [f]
	   (zero?
	      (:exit
	       (ssh username server port
		    (str "test -e " path))))))

(defmethod cp [java.lang.String java.lang.String] [^java.lang.String in ^java.lang.String out]
	   (sh-exception
	    (sh/sh "cp" "-R" in out)))

(defmethod cp [java.io.File java.io.File] [^java.io.File in ^java.io.File out]
	   (sh-exception
	    (sh/sh "cp" "-R" (.getCanonicalPath in) (.getCanonicalPath out))))

(defmethod cp [remote-file java.io.File] [in ^java.io.File out]
	   (let [{:keys [path username server port]} in]
	     (sh-exception
	      (sh/sh "ionice" "-c" "3" "scp" "-r" "-P" (str port) (str username "@" server ":" path) (.getCanonicalPath out)))))

(defmethod cp [java.io.File remote-file] [^java.io.File in out]
	   (let [{:keys [path username server port]} out]
	     (sh-exception
	      (sh/sh "ionice" "-c" "3" "scp" "-r" "-P" (str port) (.getCanonicalPath in) (str username "@" server ":" path)))))

(defmethod cp [remote-file java.lang.String] [in ^java.lang.String out]
	   (let [{:keys [path username server port]} in]
	     (sh-exception
	      (sh/sh "ionice" "-c" "3" "scp" "-r" "-P" (str port) (str username "@" server ":" path) out))))

(defmethod cp [java.lang.String remote-file] [^java.lang.String in out]
	   (let [{:keys [path username server port]} out]
	     (sh-exception
	      (sh/sh "ionice" "-c" "3" "scp" "-r" "-P" (str port) in (str username "@" server ":" path)))))

(defmethod cp [remote-file remote-file] [in out]
	   (throw (Exception. "not implemented")))

(defn new-persistent-ref
    "returns a ref with a watcher that writes to file contents of ref
as it changes. Write file is considered a 'cache', updates as best as
possible without slowing down ref"
    ([file-path the-agent]
       (let [f (file file-path)
	     writer-queue the-agent
	     dirty (ref false)
	     r (ref (load-file file-path))
	     clean (fn []
		     (dosync (ref-set dirty false))
		     (poop f
			   (binding [*print-dup* true]
			     (prn-str @r))))]
	 (add-watch r :writer (fn [k r old-state new-state]
				(dosync
				 (when-not @dirty
				   (ref-set dirty true)
				   (send-off writer-queue (fn [_]
							    (clean)))))))
	 r))
    ([file-path]
       (new-persistent-ref file-path (agent nil)))
    ([file-path the-agent default-value]
       (let [^java.io.File f (file file-path)]
	 (when-not (.exists f)
	   (poop f
		 (binding [*print-dup* true]
		   (prn-str default-value)))))
       (new-persistent-ref file-path the-agent)))

(defn to-byte-array [^java.io.File x]
  (with-open [buffer (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy x buffer)
    (.toByteArray buffer)))

(defn copy-url-to-file [url ^java.io.File f]
  (org.apache.commons.io.FileUtils/copyURLToFile (java.net.URL. url)
						 f))
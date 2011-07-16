(in-ns 'dj.toolkit)

(defn- shify [args]
  (apply str (interpose " " args)))

(defn ssh [username server port sh-code & args]
  (apply sh/sh
	 "ssh" (str username "@" server) "-p" (str port)
	 "sh" "-c"
	 (str "'" sh-code "'")
	 args))

(defn str-path
  "joins paths defined in strings together (unix)"
  [parent & children]
  (apply str (if (= (first parent)
		    \/)
	       "/"
	       "")
	 (interpose "/" (filter #(not (empty? %)) (concat (.split parent "/")
							  children)))))

(defprotocol Ipoop
  "Behave like clojure.core/spit but generically to any destination"
  (poop [dest txt] [dest txt append] "send data to file"))

(defprotocol Ieat
  "Behave like clojure.core/slurp but generically to any destination"
  (eat [dest] "obtain data from file"))

(defprotocol Imkdir
  "make a directory"
  (mkdir [dest]))

(defprotocol Ils
  "return list of files in that directory"
  (ls [dest]))

(defprotocol Irm
  "deletes path recursively"
  (rm [target]))

(defprotocol Imv
  "renames or moves file"
  (mv [target dest]))

(defprotocol Irelative-to
  "returns new abstract file relative to existing one"
  (relative-to [folder path]))

(defprotocol Iparent
  "returns parent file to existing one"
  (parent [f]))

(defprotocol Call
  "Allow a task to be executed by the executor"
  (call [executor body]))

(defprotocol RFuture
  "return a future object to manage the executor"
  (rfuture [executor body]))

(defprotocol Iget-name
  "for file like objects, get-name will return the last name in the name sequence of the path"
  (get-name [f]))

(defprotocol Iget-path
  "get str path for file like objects"
  (get-path [f]))

(defn new-file
  "returns a new java.io.File with args as files or str-paths"
  [& paths]
  (java.io.File. (apply str-path (map (fn [p]
					(if (= (type p)
					       java.io.File)
					  (.getPath p)
					  p))
				      paths))))

(extend-type java.io.File
  Ieat
  (eat [this] (slurp this))
  Ipoop
  (poop ([this txt append]
	   (if append
	     (sh/sh "sh" "-c" (str "cat - >> " (.getPath this)) :in txt)
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
      (when (.isDirectory dest)
	(doseq [f (ls dest)]
	  (rm f)))
      (.delete dest))
  Imv
  (mv [target dest]
      (.renameTo target dest))
  Irelative-to
  (relative-to [folder path]
	       (new-file folder path))
  Iparent
  (parent [f]
	  (.getParentFile f))
  Iget-path
  (get-path [f]
	    (.getPath f)))

(defmethod print-dup java.io.File [o w]
	   (print-ctor o (fn [o w] (.write w (pr-str (.getPath o)))) w))

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
       (:out (ssh username server port (shify ["cat" path]))))
  Imkdir
  (mkdir [dest]
	 (if (zero? (:exit (ssh username server port (str "mkdir -p " path))))
	   dest
	   (throw (Exception. (str "Could not make remote directory " path)))))
  Iget-name
  (get-name [f]
	    (last (.split (:path f) "/")))
  Ils
  (ls [dest]
      (map #(remote-file. (str path "/" %) username server port)
	   (let [ls-str (:out (ssh username server port (shify ["ls" path])))]
	     (if (empty? ls-str)
	       nil
	       (.split ls-str "\n")))))
  Irm
  (rm [target] (ssh username server port (shify ["rm" "-rf" path])))
  Irelative-to
  (relative-to [folder path]
	       (remote-file. (str-path (:path folder) path)
			     username server port))
  Imv
  (mv [target dest]
      (ssh username server port (shify ["mv" path dest])))
  Iparent
  (parent [f]
	  (apply str (if (= (first (:path f))
			    \/)
		       "/"
		       "")
		 (interpose "/" (filter #(not (empty? %)) (drop-last (.split (:path f) "/"))))))
  Iget-path
  (get-path [f]
	    (:path f)))

(defn new-remote-file [path username server port]
  (remote-file. path username server port))

;; Executor objects
(defrecord remote-pipe [txt username server port]
  Call
  (call [_ sh-code]
	(ssh username server port sh-code :in txt))
  RFuture
  (rfuture [_ sh-code]
	   (future [(ssh username server port sh-code :in txt)])))

(defn new-remote-pipe [txt username server port]
  (remote-pipe. txt username server port))

(defrecord remote-sh [username server port]
  Call
  (call [_ sh-code]
	(ssh username server port sh-code))
  RFuture
  (rfuture [_ sh-code]
	   (future (ssh username server port sh-code))))

(defn new-remote-sh [sh-code username server port]
  (remote-sh. username server port))

(defmulti cp #(vector (type %1) (type %2)))

(defmethod cp [java.lang.String java.lang.String] [^java.lang.String in ^java.lang.String out]
	   (sh/sh "cp" "-R" in out))

(defmethod cp [java.io.File java.io.File] [^java.io.File in ^java.io.File out]
	   (sh/sh "cp" "-R" (.getCanonicalPath in) (.getCanonicalPath out)))

(defmethod cp [remote-file java.io.File] [in ^java.io.File out]
	   (let [{:keys [path username server port]} in]
	     (sh/sh "scp" "-r" "-P" (str port) (str username "@" server ":" path) (.getCanonicalPath out))))

(defmethod cp [java.io.File remote-file] [^java.io.File in out]
	   (let [{:keys [path username server port]} out]
	     (sh/sh "scp" "-r" "-P" (str port) (.getCanonicalPath in) (str username "@" server ":" path))))

(defmethod cp [remote-file java.lang.String] [in ^java.lang.String out]
	   (let [{:keys [path username server port]} in]
	     (sh/sh "scp" "-r" "-P" (str port) (str username "@" server ":" path) out)))

(defmethod cp [java.lang.String remote-file] [^java.lang.String in out]
	   (let [{:keys [path username server port]} out]
	     (sh/sh "scp" "-r" "-P" (str port) in (str username "@" server ":" path))))

(defmethod cp [remote-file remote-file] [in out]
	   (throw (Exception. "not implemented")))

(defn unjar [^java.io.File jar-file install-dir]
  (let [jar-file (java.util.jar.JarFile. jar-file)]
    (for [entry (enumeration-seq (.entries jar-file))
	  :let [f (new-file install-dir (.getName entry))]]
      (if (.isDirectory entry)
	(.mkdirs f)
	(with-open [in-stream (.getInputStream jar-file entry)
		    out-stream (java.io.FileOutputStream. f)]
	  (loop []
	    (when (> (.available in-stream)
		     0)
	      (.write out-stream (.read in-stream))
	      (recur))))))))

(defn new-persistent-ref
    "returns a ref with a watcher that writes to file contents of ref
as it changes. Write file is considered a 'cache', updates as best as
possible without slowing down ref"
    ([file-path the-agent]
       (let [f (new-file file-path)
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
       (let [f (new-file file-path)]
	 (when-not (.exists f)
	   (poop f
		 (binding [*print-dup* true]
		   (prn-str default-value)))))
       (new-persistent-ref file-path the-agent)))
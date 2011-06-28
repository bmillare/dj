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

(defprotocol Poop
  "Behave like clojure.core/spit but generically to any destination"
  (poop [dest txt] [dest txt append] "send data to file"))

(defprotocol Eat
  "Behave like clojure.core/slurp but generically to any destination"
  (eat [dest] "obtain data from file"))

(defprotocol Mkdir
  "make a directory"
  (mkdir [dest]))

(defprotocol Ls
  "return list of files in that directory"
  (ls [dest]))

(defprotocol Rm
  "deletes path recursively"
  (rm [target]))

(defprotocol Call
  "Allow a task to be executed by the executor"
  (call [executor body]))

(defprotocol RFuture
  "return a future object to manage the executor"
  (rfuture [executor body]))

(defprotocol ISubmit
  "submit abstract function to executor, return status"
  (submit [executor afn]))

(defprotocol Get-name
  "for file like objects, get-name will return the last name in the name sequence of the path"
  (get-name [f]))

;; Destination objects
(extend-type java.io.File
  Eat
  (eat [this] (slurp this))
  Poop
  (poop ([this txt append]
	   (if append
	     (sh/sh "sh" "-c" (str "cat - >> " (.getPath this)) :in txt)
	     (spit this txt)))
	([this txt]
	   (spit this txt)))
  Mkdir
  (mkdir [this] (if (.mkdir this)
		  this
		  (throw (Exception. (str "Could not make directory " (.getCanonicalPath this))))))
  Get-name
  (get-name [f]
	    (let [n (.getName f)]
	      (if (empty? n)
		nil
		n)))
  Ls
  (ls [dest]
      (seq (.listFiles dest)))
  Rm
  (rm [dest]
      (when (.isDirectory dest)
	(doseq [f (ls dest)]
	  (rm f)))
      (.delete dest)))

(defn new-file
  "returns a new java.io.File with args as files or str-paths"
  [& paths]
  (java.io.File. (apply str-path (map (fn [p]
					(if (= (type p)
					       java.io.File)
					  (.getPath p)
					  p))
				      paths))))

(defrecord remote-file [path username server port]
  Poop
  (poop [dest txt append]
	(if append
	  (ssh username server port (str "cat - >> " path) :in txt)
	  (ssh username server port (str "cat - > " path) :in txt)))
  (poop [dest txt]
	(ssh username server port (str "cat - > " path) :in txt))
  Eat
  (eat [dest]
       (:out (ssh username server port (shify ["cat" path]))))
  Mkdir
  (mkdir [dest]
	 (if (zero? (:exit (ssh username server port (str "mkdir -p " path))))
	   dest
	   (throw (Exception. (str "Could not make remote directory " path)))))
  Get-name
  (get-name [f]
	    (last (.split (:path f) "/")))
  Ls
  (ls [dest]
      (map #(remote-file. (str path "/" %) username server port)
	   (let [ls-str (:out (ssh username server port (shify ["ls" path])))]
	     (if (empty? ls-str)
	       nil
	       (.split ls-str "\n")))))
  Rm
  (rm [target] (ssh username server port (shify ["rm" "-rf" path]))))

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
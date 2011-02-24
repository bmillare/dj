(in-ns 'dj.toolkit)

(defn- shify [args]
  (apply str (interpose " " args)))

(defn ssh [username server port sh-code & args]
  (apply sh/sh
	 "ssh" (str username "@" server) "-p" (str port)
	 "sh" "-c"
	 (str "'" sh-code "'")
	 args))

(defprotocol Poop
  "Behave like clojure.core/spit but generically to any destination"
  (poop [dest txt] "send data to file"))

(defprotocol Eat
  "Behave like clojure.core/slurp but generically to any destination"
  (eat [dest] "obtain data from file"))

(defprotocol Mkdir
  "make a directory"
  (mkdir [dest]))

(defprotocol Ls
  "return list of files in that directory"
  (ls [dest]))

(defprotocol Call
  "Allow a task to be executed by the executor"
  (call [executor body]))

(defprotocol RFuture
  "return a future object to manage the executor"
  (rfuture [executor body]))

(defprotocol Get-name
  "for file like objects, get-name will return the last name in the name sequence of the path"
  (get-name [f]))

;; Destination objects
(extend-type java.io.File
  Eat
  (eat [this] (slurp this))
  Poop
  (poop [this txt] (spit this txt))
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
      (seq (.listFiles dest))))

(defrecord remote-file [path username server port]
  Poop
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
	       (.split ls-str "\n"))))))

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


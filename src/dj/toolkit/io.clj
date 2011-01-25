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

(defprotocol Call
  "Allow a task to be executed by the executor"
  (call [executor body]))

;; Destination objects
(defrecord remote-file [path username server port]
  Poop
  (poop [dest txt]
	(ssh username server port (str "cat - > " path) :in txt))
  Eat
  (eat [dest]
       (:out (ssh username server port (shify ["cat" path])))))

(defn new-remote-file [path username server port]
  (remote-file. path username server port))

;; Executor objects
(defrecord remote-pipe [txt username server port]
  Call
  (call [_ sh-code]
	(ssh username server port sh-code :in txt)))

(defn new-remote-pipe [txt username server port]
  (remote-pipe. txt username server port))

(defrecord remote-sh [username server port]
  Call
  (call [_ sh-code]
	(ssh username server port sh-code)))

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


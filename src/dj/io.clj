(ns dj.io
  (:import [java.io File FileNotFoundException IOException])
  (:require [clojure.java.io :as io]))

(defn file
  [& args]
  (.getCanonicalFile (apply io/file args)))

(defn string
  [^java.io.File f]
  (.getCanonicalPath f))

(defn make-directory!
  "makes directory or directories if it doesn't exist already, returns path to folder"
  [^File directory]
  (when-not (.exists directory)
    (when-not (.mkdirs directory)
      (throw (IOException. (str "cannot create directory "
				(.getCanonicalPath directory)
				" please clean up")))))
  directory)

(defn delete-recursive
  "inclusive delete recursively"
  [^File f]
  (if (.isDirectory f)
    (do (doseq [child (.listFiles f)]
	  (delete-recursive child))
	(.delete f))
    (.delete f)))

(defn make-tmp-folder!
  "makes tmp folder with unique name in directory, returns path to folder"
  [^File directory]
  (if (.exists directory)
    (loop []
      (let [file-list (seq (.listFiles directory))
	    largest-count (if file-list
			    (apply max (for [f file-list] (Integer/parseInt (.getName f))))
			    0)
	    tmp-folder-path (file directory (str (inc largest-count)))]
	(if (.mkdir tmp-folder-path)
	  tmp-folder-path
	  (if (.exists tmp-folder-path)
	    (recur)
	    (throw (IOException. (.getCanonicalPath tmp-folder-path)))))))
    (throw (FileNotFoundException. (.getCanonicalPath directory)))))

(defmacro with-tmp-directory
  "creates directory in specified directory, executes body, then deletes directory"
  [temporary-folder-symbol directory-file & body]
  `(let [~temporary-folder-symbol (make-tmp-folder! (make-directory! ~directory-file))]
     (try
       ~@body
       (finally (delete-recursive ~temporary-folder-symbol)))))

(defn unjar [^java.io.File jar-file install-dir]
  (let [jar-file (java.util.jar.JarFile. jar-file)]
    (for [entry (enumeration-seq (.entries jar-file))
	  :let [f (file install-dir (.getName entry))]]
      (if (.isDirectory entry)
	(.mkdirs f)
	(with-open [in-stream (.getInputStream jar-file entry)
		    out-stream (java.io.FileOutputStream. f)]
	  (loop []
	    (when (> (.available in-stream)
		     0)
	      (.write out-stream (.read in-stream))
	      (recur))))))))
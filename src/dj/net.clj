(ns dj.net
  (:import [java.io File FileOutputStream BufferedInputStream BufferedReader InputStreamReader])
  (:import [java.net URL])
  (:require [clojure.contrib [duck-streams :as duck-streams]]))

(def repository-urls ["http://repo1.maven.org/maven2"
		      "http://clojars.org/repo/"])

(defn blank?
  "True if s is nil, empty, or contains only whitespace."
  [#^String s]
  (every? (fn [#^Character c] (Character/isWhitespace c)) s))

(defn- extract-url-filename [url]
  (let [filename (.getFile url)]
    (if (or (blank? filename)
	    (= filename "/"))
      "index.html"
      (subs filename 1))))

(defn- wget-binary [con content-length]
  (with-open [stream (BufferedInputStream. (.getInputStream con))]
    (let [data (make-array Byte/TYPE content-length)]
      (loop [offset 0]
	(if (< offset content-length)
	  (let [bytesRead (.read stream
				 data
				 offset
				 (- content-length
				    offset))]
	    (if (= bytesRead -1)
	      data
	      (recur (+ offset bytesRead))))
	  data)))))

(defn- wget-text [url-obj]
  (with-open [buf (-> url-obj
		      (.openStream)
		      (InputStreamReader.)
		      (BufferedReader.))]
    (apply str (line-seq buf))))

(defn wget [url-address]
  (let [url (URL. url-address)
	filename (extract-url-filename url)
	con (.openConnection url)
	content-length (.getContentLength con)]
    (if (or (= -1 content-length)
	    (.startsWith (.getContentType con) "text/"))
      (duck-streams/spit filename (wget-text url))
      (with-open [out-file (FileOutputStream. filename)]
	(.write out-file (wget-binary con content-length))))))

(defn resolve-url []
  "takes dependency form and returns URLs")

(defn download-dependency []
  "takes dependency and downloads file")
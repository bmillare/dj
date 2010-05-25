(ns dj.net
  (:import [java.io File FileOutputStream BufferedInputStream BufferedReader InputStreamReader])
  (:import [java.net URL]))

(defn blank?
  "True if s is nil, empty, or contains only whitespace."
  [#^String s]
  (every? (fn [#^Character c] (Character/isWhitespace c)) s))

(defn- extract-url-filename [url]
  (let [filename (.getFile url)]
    (if (or (blank? filename)
	    (= filename "/"))
      "index.html"
      (.getName (File. filename)))))

(defn- wget-fixed
  "returns content from http connection in an array"
  [con content-length]
  (with-open [stream (BufferedInputStream. (.getInputStream con))]
    (let [data (make-array Byte/TYPE content-length)]
      (loop [offset 0]
	(if (< offset content-length)
	  (let [bytes-read (.read stream
				 data
				 offset
				 (- content-length
				    offset))]
	    (if (= bytes-read -1)
	      data
	      (recur (+ offset bytes-read))))
	  data)))))

(defn wget!
  "takes string or URL url-address and downloads file into directory,
returns path to that file"
  [url-address directory]
  (let [url (if (string? url-address) (URL. url-address) url-address)
	filename (File. directory (extract-url-filename url))
	con (.openConnection url)
	content-length (.getContentLength con)]
    (if (> content-length 0)
     (with-open [out (FileOutputStream. filename)]
       (.write out (wget-fixed con content-length)))
     (throw (Exception. (str "TODO: Implement downloader for undefined content-length: "
			     content-length))))
    filename))

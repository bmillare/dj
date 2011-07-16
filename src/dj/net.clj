(ns dj.net
  (:require [dj.toolkit :as tk])
  (:import [java.io File FileOutputStream BufferedInputStream BufferedReader InputStreamReader])
  (:import [java.net URL HttpURLConnection]))

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

(defn wget-str!
  "takes string or URL url-address and returns what it reads as string"
  [url-address]
  (let [url (if (string? url-address) (URL. url-address) url-address)]
    (with-open [stream (java.io.BufferedReader. (java.io.InputStreamReader. (.openStream url)))]
      (apply str (interpose "\n" (take-while identity (repeatedly #(.readLine stream))))))))

(defn wget!
  "takes string or URL url-address and downloads file into directory,
returns path to that file"
  [url-address directory]
  (let [url (if (string? url-address) (URL. url-address) url-address)
	out-file (File. directory (extract-url-filename url))
	con (.openConnection url)
	content-length (.getContentLength con)]
    (if (> content-length 0)
     (with-open [out (FileOutputStream. out-file)]
       (.write out (wget-fixed con content-length)))
     (tk/poop out-file
	      (with-open [stream (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream con)))]
		(apply str (interpose "\n" (take-while identity (repeatedly #(.readLine stream))))))))
    out-file))

(defn exists?
  [URLName]
  (HttpURLConnection/setFollowRedirects false)
  (= (.getResponseCode
      (doto (.openConnection (URL. URLName))
	(.setInstanceFollowRedirects false)
	(.setRequestMethod "HEAD")))
     HttpURLConnection/HTTP_OK))
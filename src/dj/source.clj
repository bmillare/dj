(ns dj.source
  (:refer-clojure :exclude [find re-find])
  (:require [dj.io]))

(defn find
  "searchers directory for files that when passed to f, return true"
  [dir f]
  (reduce (fn [ret file]
            (if (.isDirectory file)
              (concat (find file f) ret)
              (if (f file)
                (cons file ret)
                ret)))
          ()
          (dj.io/ls dir)))

(defn re-find
  "searches directory for files that have name re"
  [dir re]
  (find dir
        (fn [file]
          (clojure.core/re-find re (dj.io/get-name file)))))

(defn search
  "searches directory for files that have re-text"
  [dir re-filename re-text]
  (for [f (re-find dir re-filename)
        :when (clojure.core/re-find re-text (dj.io/eat f))]
    f))

(defn defs
  "given a file, searches for all 'def' like forms and returns first
  arg"
  [f]
  (let [re #"\(def\S*\s+(\S+)"]
    (map (fn [m]
           (second
            (re-matches re
                        m)))
         (dj/re-find-all re
                         (dj.io/eat f)))))

(defn map-files
  "creates a hashmap of filenames to output of fn given file"
  [f files]
  (reduce (fn [m file]
            (assoc m
              (dj.io/get-path file)
              (f file)))
          {}
          files))
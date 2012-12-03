(ns dj.source
  (:refer-clojure :exclude [re-find])
  (:require [dj.io]))

(defn find [dir f]
  (reduce (fn [ret file]
            (if (f file)
              (if (.isDirectory file)
                (concat (find file f) ret)
                (cons file ret))
              ret))
          ()
          (dj.io/ls dir)))

(defn re-find [dir re]
  (find dir
        (fn [file]
          (clojure.core/re-find re (dj.io/get-name file)))))

(defn search [dir re-filename re-text]
  (for [f (re-find dir re-filename)
        :when (clojure.core/re-find re-text (dj.io/eat f))]
    f))

(defn defs [f]
  (let [re #"\(def\w*\s+(\S+)"]
    (map (fn [m]
           (second
            (re-matches re
                        m)))
         (dj/re-find-all re
                         (dj.io/eat f)))))
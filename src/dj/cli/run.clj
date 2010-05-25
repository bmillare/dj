(ns dj.cli.run
  "run clojure code from command line")

(defn main
  "USAGE: dj run \"(put arbitrary clojure code here)\""
  [& [code]]
  (prn (eval (read-string code))))
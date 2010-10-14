(ns dj.toolkit)

(defmacro log
  [code]
  `(let [c# ~code]
     (prn '~code)
     (prn c#)
     c#))
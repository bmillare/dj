(in-ns 'dj.toolkit)

(defprotocol Build
  (build [format data]))

(defprotocol Parse
  (parse [format txt]))
(in-ns 'dj.toolkit)

(defn gui-doc [ns]
  (let [name-str (name ns)]
    (dj.toolkit/text-box (str name-str " names gui") (dj.toolkit/doc-ns ns))
    (dj.toolkit/scroll-box (str name-str " docs gui") (apply str
							     (interpose "\n"
									(sort (map #(with-out-str (print-doc (meta %)))
										   (ns-vars :publics (the-ns ns))))))
			   70 30)))
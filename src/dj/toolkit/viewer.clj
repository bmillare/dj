(in-ns 'dj.toolkit)

(defn text-box [title data]
  (doto (JFrame. ^String title)
    (.setContentPane (doto (javax.swing.JTextArea. (if (empty? data)
						     "Empty data"
						     (if (= java.lang.String
							    (type data))
						       (print-str data)
						       (pr-str data))))
		       (.setFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 14))))
    (.pack)
    (.setVisible true)))

(defn scroll-box [title text columns rows]
  (doto (JFrame. ^String title)
    (.setContentPane (javax.swing.JScrollPane. (doto (javax.swing.JTextArea. text rows columns)
						 (.setFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 14)))))
    (.pack)
    (.setVisible true)))

;; need to use print so that lazy sequences are realized
(defn str-newline-seq [s]
  (apply str (interpose "\n" (map #(with-out-str (pr %)) s))))

(defn sequence-box [title s]
  (scroll-box title (str-newline-seq s) 70 (min (count s) 70)))
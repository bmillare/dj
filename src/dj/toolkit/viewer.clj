(in-ns 'dj.toolkit)

(defn text-box [title text]
  (doto (JFrame. title)
    (.setContentPane (doto (javax.swing.JTextArea. text)
		       (.setFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 14))))
    (.pack)
    (.setVisible true)))

(defn scroll-box [title text columns rows]
  (doto (JFrame. title)
    (.setContentPane (javax.swing.JScrollPane. (doto (javax.swing.JTextArea. text rows columns)
						 (.setFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 14)))))
    (.pack)
    (.setVisible true)))

(defn str-newline-seq [s]
  (apply str (interpose "\n" (map str s))))

(defn sequence-box [title s]
  (scroll-box title (str-newline-seq s) 70 10))

#_ (do
     (in-ns 'dj.toolkit)
     (SwingUtilities/invokeLater (fn [] (dumb-frame "wtf " (javax.swing.JTextArea. "hello" 10 10))))
     (SwingUtilities/invokeLater (fn [] (scroll-box "asdf" "hello there asldkf alsfdkj laskjdflkasdfl al\nsjdf \nlkaj slk\ndf ja\nlksj dfl\na sio\nydfj apoijfe oipfj aopiwe fjiopaowpief oipawejoipfjaopwefjpoaspef joapisofepijoapsejfoipa joefp" 70 10)))
     (SwingUtilities/invokeLater (fn [] (sequence-box "s" (range 20))))
     (sequence-box "blah" (dj.toolkit/var-apropos "remove" :all))
     (text-box "blah" (with-out-str (clojure.repl/doc remove-ns)))
     (remove-ns 'dumb-frame)
     )
(in-ns 'dj.toolkit)

(defn dumb-frame [title container]
  (doto (JFrame. title)
      (.setContentPane container)
      (.pack)
      (.setVisible true)))

(defn text-box [title text]
  (doto (JFrame. title)
    (.setContentPane (javax.swing.JTextArea. text))
    (.pack)
    (.setVisible true)))

(defn scroll-box [title text columns rows]
  (doto (JFrame. title)
    (.setContentPane (javax.swing.JScrollPane. (doto (javax.swing.JTextArea. text rows columns)
						 (.setFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 14)))))
    (.pack)
    (.setVisible true)))

#_ (do
     (SwingUtilities/invokeLater (fn [] (dumb-frame "wtf " (javax.swing.JTextArea. "hello" 10 10))))
     (SwingUtilities/invokeLater (fn [] (scroll-box "asdf" "hello there asldkf alsfdkj laskjdflkasdfl al\nsjdf \nlkaj slk\ndf ja\nlksj dfl\na sio\nydfj apoijfe oipfj aopiwe fjiopaowpief oipawejoipfjaopwefjpoaspef joapisofepijoapsejfoipa joefp" 70 10)))
     )
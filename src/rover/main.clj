(ns rover.main
  (:gen-class)
  (:require 
    [clojure.core.async :as a :refer [<! >! <!! >!! chan put! take! go alt! alts! do-alts close! timeout pipe mult tap untap 
                                      pub sub unsub mix admix unmix dropping-buffer sliding-buffer pipeline pipeline-async to-chan! thread]]
    [clojure.string]
    [clojure.java.io :as io]
    [clojure.test.check.generators :as pawny.generators]
    [clojure.repl :refer [source doc dir]]
    )
  (:import
    (javax.swing JFrame WindowConstants ImageIcon JPanel JScrollPane JTextArea BoxLayout JEditorPane ScrollPaneConstants)
    (javax.swing.border EmptyBorder)
    (java.awt Canvas Graphics Graphics2D Shape Color Polygon Dimension BasicStroke)
    (java.awt.event KeyListener KeyEvent)
    (java.awt.geom Ellipse2D Ellipse2D$Double)
  )    
)

#_(println (System/getProperty "clojure.core.async.pool-size"))
(do (set! *warn-on-reflection* true) (set! *unchecked-math* true))

(defonce stateA (atom nil))
(def ^:dynamic ^JFrame jframe nil)
(def ^:dynamic ^Canvas canvas nil)
(def ^:dynamic ^JTextArea repl nil)
(def ^:dynamic ^JTextArea output nil)
(def ^:dynamic ^JScrollPane output-scroll nil)
(def ^:dynamic ^Graphics2D graphics nil)
(defonce ns* (find-ns 'rover.main))

(defn eval*
  [form]
  (let [string-writer (java.io.StringWriter.)
        result (binding [*ns* ns*
                         *out* string-writer]
                  (eval form)
                )]
    (doto output
      (.append "=> ")
      (.append (str form))
      (.append "\n")
      (.append (str string-writer))
      (.append (pr-str result))
      (.append "\n")
    )

    (go
      (<! (timeout 10))
      (let [scrollbar (.getVerticalScrollBar output-scroll)]
        (.setValue scrollbar (.getMaximum scrollbar) )
      )
    )
  )
)

(defn clear-canvas
  []
  (.clearRect graphics 0 0 (.getWidth canvas)  (.getHeight canvas))
)

(defn clear
  []
  (.setText output "")
)

(defn move
  "move rover to x y coordinate"
  [x y]
  (swap! stateA update :rover merge {:x x :y y})
  nil
)


(defn window
  []
  (let [jframe (JFrame. "i am rover program")
        panel (JPanel.)
        layout (BoxLayout. panel BoxLayout/X_AXIS)
        code-panel (JPanel.)
        code-layout (BoxLayout. code-panel BoxLayout/Y_AXIS)
        canvas (Canvas.)
        repl (JTextArea. 1 80)
        output (JTextArea. 14 80)
        output-scroll (JScrollPane.)
        editor (JEditorPane.)
        editor-scroll (JScrollPane.)
        ]

  (when-let [url (io/resource "icon.png")]
    (.setIconImage jframe (.getImage (ImageIcon. url)))
  )

  (doto editor
    (.setBorder (EmptyBorder. #_top 0 #_left 0 #_bottom 0 #_right 20 ))
  )

  (doto editor-scroll
    (.setViewportView editor)
    (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER)
    (.setPreferredSize (Dimension. 1000 1300))
  )
  
  (doto output
    (.setEditable false)
  )

  (doto output-scroll
    (.setViewportView output)
    (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER)
  )

  (doto repl
    (.addKeyListener (reify KeyListener
                      (keyPressed 
                        [_ event]
                          (when (= (.getKeyCode ^KeyEvent event) KeyEvent/VK_ENTER)
                            (.consume ^KeyEvent event)
                          )
                        )
                      (keyReleased 
                        [_ event]
                         (when (= (.getKeyCode ^KeyEvent event) KeyEvent/VK_ENTER)
                            (-> (.getText repl) (clojure.string/trim) (clojure.string/trim-newline) (read-string) (eval*))
                            (.setText repl "")
                          )
                        )
                      (keyTyped 
                        [_ event])
                       ))
  )

  (doto code-panel
    (.setLayout code-layout)
    (.add editor-scroll)
    (.add output-scroll)
    (.add repl)
  )

  (doto canvas
    (.setSize 1400 1600)
  )

  (doto panel
    (.setLayout layout)
    (.add code-panel)
    (.add canvas)
  )

  (doto jframe
    (.setDefaultCloseOperation WindowConstants/DISPOSE_ON_CLOSE)
    (.setSize 2400 1600)
    (.setLocation 1300 200)
    (.add panel)
    (.setVisible true)
  )

  (alter-var-root #'rover.main/jframe (constantly jframe))
  (alter-var-root #'rover.main/canvas (constantly canvas))
  (alter-var-root #'rover.main/output-scroll (constantly output-scroll))
  (alter-var-root #'rover.main/repl (constantly repl))
  (alter-var-root #'rover.main/output (constantly output))
  (alter-var-root #'rover.main/graphics (constantly (.getGraphics canvas)))

  (add-watch stateA :watch-fn 
    (fn [ref wathc-key old-state new-state]
      
      (clear-canvas)
      (.setPaint graphics (Color. 240 231 231))
      (.fillRect graphics 0 0 (.getWidth canvas) (.getHeight canvas))
      
      (doseq [[k value] new-state]

        (condp identical? (:shape value)
          
          :rover
          (let [{:keys [^int x ^int y name]} value
                body (Polygon. (int-array [x (+ x 15) (+ x 15) x]) (int-array [(+ y 10) (+ y 10) (+ y 45) (+ y 45)]) 4)
                ]
             (.setColor graphics Color/WHITE)
             (.fill graphics body)
             (.setColor graphics Color/BLACK)
             #_(.setStroke graphics (BasicStroke. 1))
             (.drawLine graphics (- x 6) (+ y 14) (- x 6) (+ y 17))
             (.drawLine graphics (- x 6) (+ y 26) (- x 6) (+ y 29))
             (.drawLine graphics (- x 6) (+ y 38) (- x 6) (+ y 41))

             (.drawLine graphics (+ x 15 6) (+ y 14) (+ x 15 6) (+ y 17))
             (.drawLine graphics (+ x 15 6) (+ y 26) (+ x 15 6) (+ y 29))
             (.drawLine graphics (+ x 15 6) (+ y 38) (+ x 15 6) (+ y 41))
            )
          
          :martian
          (let [{:keys [^int x ^int y name]} value
                 face (Polygon. (int-array [x (+ x 30) (+ x 18) (+ x 12)]) (int-array [(+ y 10) (+ y 10) (+ y 40) (+ y 40)]) 4)
                 left-eye (Ellipse2D$Double. (+ x 5) (+ y 15) 8 4)
                 right-eye (Ellipse2D$Double. (+ x 17) (+ y 15) 8 4)
                 ]
             (.setPaint graphics (Color. 3 165 106))
             (.fill graphics face)
             (.setPaint graphics Color/WHITE)
             (.fill graphics left-eye)
             (.fill graphics right-eye)
            )

          :tower
          (let [{:keys [^int x ^int y name]} value
                 shape (Polygon. (int-array [(- x 5) x (+ x 5)]) (int-array [(+ y 30) (+ y 10) (+ y 30)  ]) 3)
                 ]
             (.setStroke graphics (BasicStroke. 4))
             (.setColor graphics Color/DARK_GRAY)
             (.draw graphics shape)
            )
        
          (do nil)
        )

      )
     
     ))

  (go
    (<! (timeout 100))

    (let [rover {:rover {:name "rover"
                         :shape :rover
                         :x (+ 100 (rand-int 1200))
                         :y (+ 100 (rand-int 1400))}}
          martians (into {}
                      (comp
                        (map (fn [i] 
                          {:name (format "martian %s" i)
                           :shape :martian
                           :x (+ 100 (rand-int 1200))
                           :y (+ 100 (rand-int 1400)) }
                        ))
                        (map (fn [value] [(:name value) value]))
                      )
                      (range 0 10)
                    )
          towers  (into {}
                      (comp
                        (map (fn [i] 
                          {:name (format "tower %s" i)
                           :shape :tower
                           :x (+ 100 (rand-int 1200))
                           :y (+ 100 (rand-int 1400)) }
                        ))
                        (map (fn [value] [(:name value) value]))
                      )
                      (range 0 10)
                    )         
          ]
      (swap! stateA merge rover martians towers)


      (eval* '(dir rover.main))
      (eval* '(doc move))
    )
  )
  nil
  )
)

(defn reload
  []
  (require 
    '[rover.main]
    :reload)
)

(defn -main
  [& args]
  (let []
    (reset! stateA {})

    (window)

  )
)

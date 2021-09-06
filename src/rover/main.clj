(ns rover.main
  (:gen-class)
  (:require 
    [clojure.core.async :as a :refer [<! >! <!! >!! chan put! take! go alt! alts! do-alts close! timeout pipe mult tap untap 
                                      pub sub unsub mix admix unmix dropping-buffer sliding-buffer pipeline pipeline-async to-chan! thread]]
    [clojure.string]
    [clojure.java.io :as io]
    [clojure.test.check.generators :as pawny.generators]
    [clojure.spec.alpha :as s]
    [clojure.repl :refer [source doc dir]]
    )
  (:import
    (javax.swing JFrame WindowConstants ImageIcon JPanel JScrollPane JTextArea BoxLayout JEditorPane ScrollPaneConstants)
    (javax.swing.border EmptyBorder)
    (java.awt Canvas Graphics Graphics2D Shape Color Polygon Dimension BasicStroke)
    (java.awt.event KeyListener KeyEvent)
    (java.awt.geom Ellipse2D Ellipse2D$Double Point2D$Double)
  )    
)

#_(println (System/getProperty "clojure.core.async.pool-size"))
(do (set! *warn-on-reflection* true) (set! *unchecked-math* true))

(defonce stateA (atom nil))
(def ^:dynamic ^JFrame jframe nil)
(def ^:dynamic ^Canvas canvas nil)
(def ^:dynamic ^JTextArea repl nil)
(def ^:dynamic ^JTextArea output nil)
(def ^:dynamic ^JEditorPane editor nil)
(def ^:dynamic ^JScrollPane output-scroll nil)
(def ^:dynamic ^Graphics2D graphics nil)
(defonce ns* (find-ns 'rover.main))
(def ^:const energy-per-move 100)

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

(defn set-destination
  "set rover's destination x y"
  [x y]
  (swap! stateA update :rover merge {:destination-x x 
                                     :destination-y y})
  nil
)

(defn vec-subtract
  [a b]
  (mapv - a b)
)

(defn vec-scalar-divide
  [a x]
  (mapv #(/ % x) a)
)

(defn vec-scalar-multiply
  [a x]
  (mapv #(* % x) a)
)

(defn vec-distance
  [a b]
  (Math/sqrt 
    (+ 
      (Math/pow ^int (- (first a) (first b)) 2)
      (Math/pow ^int (- (second a) (second b)) 2)
    )
  )
)

(defn vec-length
  [a]
  (Math/sqrt 
    (+
      (Math/pow ^int (first a) 2)
      (Math/pow ^int (second a) 2)
    )
  )
)

(defn vec-normalize
  [a]
  (vec-scalar-divide a (vec-length a))
)

(defn move
  "move rover one step towards destination x y"
  []
  (let [{:keys [energy ^int x ^int y ^int destination-x ^int destination-y]} (:rover @stateA)
        path-vec (vec-subtract [destination-x destination-y] [x y])
        path-vec-length (vec-length path-vec)
        path-vec-unit (vec-normalize path-vec)
        one-move-vec (vec-scalar-multiply path-vec-unit energy-per-move)
        ]
    (swap! stateA update :rover merge {:x (int (first one-move-vec)) :y (int (second one-move-vec))})
  )
  nil
)

(defn can-move?
  []
  (>= (get-in @stateA [:rover :energy]) energy-per-move)
)

(defn transmit
  "evaluate code in editor and send it to rover"
  []
  (-> (.getText editor) (clojure.string/trim) (clojure.string/trim-newline) (read-string) (eval*))
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
  (alter-var-root #'rover.main/editor (constantly editor))
  (alter-var-root #'rover.main/graphics (constantly (.getGraphics canvas)))

  (add-watch stateA :watch-fn 
    (fn [ref wathc-key old-state new-state]
      
      (clear-canvas)
      (.setPaint graphics (Color. 237 211 175 200))
      (.fillRect graphics 0 0 (.getWidth canvas) (.getHeight canvas))
      
      (doseq [[k value] new-state]

        (condp identical? (:shape value)
          
          :rover
          (let [{:keys [^int x 
                        ^int y 
                        name 
                        ^int energy
                        ^int destination-x
                        ^int destination-y
                        ]} value
                body (Polygon. (int-array [x (+ x 15) (+ x 15) x]) (int-array [(+ y 10) (+ y 10) (+ y 45) (+ y 45)]) 4)
                ]
            (.setColor graphics Color/WHITE)
            (.fill graphics body)
            (.setColor graphics Color/BLACK)
            (.setStroke graphics (BasicStroke. 4))
            (.drawLine graphics (- x 6) (+ y 14) (- x 6) (+ y 17))
            (.drawLine graphics (- x 6) (+ y 26) (- x 6) (+ y 29))
            (.drawLine graphics (- x 6) (+ y 38) (- x 6) (+ y 41))

            (.drawLine graphics (+ x 15 6) (+ y 14) (+ x 15 6) (+ y 17))
            (.drawLine graphics (+ x 15 6) (+ y 26) (+ x 15 6) (+ y 29))
            (.drawLine graphics (+ x 15 6) (+ y 38) (+ x 15 6) (+ y 41))
          
            (.setStroke graphics (BasicStroke. 1))
            (.setColor graphics Color/BLUE)
            (.drawOval graphics (- x energy) (- y energy) (* energy 2) (* energy 2))

            (when (and destination-x destination-y)
              (.setColor graphics Color/ORANGE)
              (.drawLine graphics x y destination-x destination-y)
            )

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
          
          :metallic-insects-cloud
          (let [{:keys [xs ys name]} value
                 shape (Polygon. (int-array xs) (int-array ys) (count xs))
                 ]
             (.setStroke graphics (BasicStroke. 1))
             (.setColor graphics Color/LIGHT_GRAY)
             (.fill graphics shape)

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
                         :y (+ 100 (rand-int 1400))
                         :destination-x nil
                         :destination-y nil
                         :energy 500
                         }}
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
          metallic-insects-clouds (into {}
                                    (comp
                                      (map (fn [i] 
                                        (let [x (+ 100 (rand-int 1200))
                                              y (+ 100 (rand-int 1400))
                                              width (+ 30 (rand-int 70)) 
                                              height (+ 30 (rand-int 70))
                                              gen-x (pawny.generators/large-integer* {:min x :max (+ x width)})
                                              gen-y (pawny.generators/large-integer* {:min y :max (+ y height)})
                                              n-points (+ 3 (rand-int 10))
                                              xs (->
                                                  (pawny.generators/vector-distinct gen-x {:num-elements n-points :max-tries 20})
                                                  (pawny.generators/generate)
                                                  )
                                              ys (->
                                                  (pawny.generators/vector-distinct gen-y {:num-elements n-points :max-tries 20})
                                                  (pawny.generators/generate)
                                                  (sort)
                                                  )
                                              ]
                                          { :name (format "metallic-insects-cloud %s" i)
                                            :shape :metallic-insects-cloud
                                            :xs xs
                                            :ys ys}
                                        )
                                      ))
                                      (map (fn [value] [(:name value) value]))
                                    )
                                    (range 0 10)
                                  )         
          ]
      (swap! stateA merge rover martians towers metallic-insects-clouds)


      (eval* '(list 'move))
      (eval* '(doc move))

      (.setText editor "
(go 
  (set-destination 1000 1000)
  (loop []
    (<! (timeout 1000))
    (when (can-move?)
      (move)
      (recur)
    )
  )
)
                        ")

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

(ns Watney.main
  (:gen-class)
  (:require
   [clojure.core.async :as a :refer [<! >! <!! >!! chan put! take! go alt! alts! do-alts close! timeout pipe mult tap untap
                                     pub sub unsub mix admix unmix dropping-buffer sliding-buffer pipeline pipeline-async to-chan! thread]]
   [clojure.string]
   [clojure.java.io :as io]
   [clojure.test.check.generators :as Pawny.generators]
   [clojure.spec.alpha :as s]
   [clojure.repl :refer [source doc dir]])
  (:import
   (javax.swing JFrame WindowConstants ImageIcon JPanel JScrollPane JTextArea BoxLayout JEditorPane ScrollPaneConstants)
   (javax.swing.border EmptyBorder)
   (java.awt Canvas Graphics Graphics2D Shape Color Polygon Dimension BasicStroke)
   (java.awt.event KeyListener KeyEvent MouseListener MouseEvent)
   (java.awt.geom Ellipse2D Ellipse2D$Double Point2D$Double)))

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
(defonce ns* (find-ns 'Watney.main))

(def ^:const energy-per-move 100)
(def ^:const canvas-width 1600)
(def ^:const canvas-height 1600)
(def ^:const tile-size 32)

(defn eval*
  [form]
  (let [string-writer (java.io.StringWriter.)
        result (binding [*ns* ns*
                         *out* string-writer]
                 (eval form))]
    (doto output
      (.append "=> ")
      (.append (str form))
      (.append "\n")
      (.append (str string-writer))
      (.append (pr-str result))
      (.append "\n"))

    (go
      (<! (timeout 10))
      (let [scrollbar (.getVerticalScrollBar output-scroll)]
        (.setValue scrollbar (.getMaximum scrollbar))))))

(defn clear-canvas
  []
  (.clearRect graphics 0 0 (.getWidth canvas)  (.getHeight canvas)))

(defn clear
  []
  (.setText output ""))

(defn set-route
  "set Watney's route [[x y] [x y] ..] "
  [route]
  (swap! stateA update :Watney merge {:route route})
  nil)

(defn add-route-point
  "add point to Watney's route"
  [x y]
  (swap! stateA update-in [:Watney :route] (fn [route] (-> route (conj [x y]) (vec))))
  nil)

(defn vec-subtract
  [a b]
  (mapv - a b))

(defn vec-sum
  [a b]
  (mapv + a b))

(defn vec-scalar-divide
  [a x]
  (mapv #(/ % x) a))

(defn vec-scalar-multiply
  [a x]
  (mapv #(* % x) a))

(defn vec-distance
  [a b]
  (Math/sqrt
   (+
    (Math/pow ^int (- (first a) (first b)) 2)
    (Math/pow ^int (- (second a) (second b)) 2))))

(defn vec-length
  [a]
  (Math/sqrt
   (+
    (Math/pow ^int (first a) 2)
    (Math/pow ^int (second a) 2))))

(defn vec-normalize
  [a]
  (vec-scalar-divide a (vec-length a)))

(defn move
  "move Watney one step towards next route-point x y"
  []
  (let [{:keys [energy ^int x ^int y route ^int energy]} (:Watney @stateA)
        [^int route-point-x ^int route-point-y :as route-point] (first route)
        route-point-vec (vec-subtract route-point [x y])
        route-point-vec-unit (vec-normalize route-point-vec)
        energy-to-route-point (int (vec-length route-point-vec))
        energy-this-move (min energy energy-per-move energy-to-route-point)
        move-vec (vec-scalar-multiply route-point-vec-unit energy-this-move)
        next-point  (if (== energy-this-move energy-to-route-point) route-point (vec-sum [x y] move-vec))]
    (-> @stateA
        (update :Watney merge {:x (int (first next-point)) :y (int (second next-point))})
        (update-in [:Watney :energy] (fn [value] (max 0 (- value energy-this-move))))
        (update :Watney assoc :route (if (= next-point route-point) (rest route) route))
        (->> (swap! stateA merge))))
  nil)

(defn can-move?
  []
  (and
   (not (empty? (get-in @stateA [:Watney :route])))
   (> (get-in @stateA [:Watney :energy]) 0)))

(defn transmit
  "evaluate code in editor and send it to Watney"
  []
  (-> (.getText editor) (clojure.string/trim) (clojure.string/trim-newline) (read-string) (eval*)))


(defn window
  []
  (let [jframe (JFrame. "i'm gonna have to science the sh*t out of this")
        panel (JPanel.)
        layout (BoxLayout. panel BoxLayout/X_AXIS)
        code-panel (JPanel.)
        code-layout (BoxLayout. code-panel BoxLayout/Y_AXIS)
        canvas (Canvas.)
        repl (JTextArea. 1 80)
        output (JTextArea. 14 80)
        output-scroll (JScrollPane.)
        editor (JEditorPane.)
        editor-scroll (JScrollPane.)]

    (when-let [url (io/resource "icon.png")]
      (.setIconImage jframe (.getImage (ImageIcon. url))))

    (doto editor
      (.setBorder (EmptyBorder. #_top 0 #_left 0 #_bottom 0 #_right 20)))

    (doto editor-scroll
      (.setViewportView editor)
      (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER)
      (.setPreferredSize (Dimension. 800 1300)))

    (doto output
      (.setEditable false))

    (doto output-scroll
      (.setViewportView output)
      (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER))

    (doto repl
      (.addKeyListener (reify KeyListener
                         (keyPressed
                           [_ event]
                           (when (= (.getKeyCode ^KeyEvent event) KeyEvent/VK_ENTER)
                             (.consume ^KeyEvent event)))
                         (keyReleased
                           [_ event]
                           (when (= (.getKeyCode ^KeyEvent event) KeyEvent/VK_ENTER)
                             (-> (.getText repl) (clojure.string/trim) (clojure.string/trim-newline) (read-string) (eval*))
                             (.setText repl "")))
                         (keyTyped
                           [_ event]))))

    (doto code-panel
      (.setLayout code-layout)
      (.add editor-scroll)
      (.add output-scroll)
      (.add repl))

    (doto canvas
      (.setSize canvas-width canvas-height)
      (.addMouseListener (reify MouseListener
                           (mouseClicked
                             [_ event]
                             (when (= (.getButton ^MouseEvent event) MouseEvent/BUTTON3)
                               (swap! stateA merge {:coordinate [(.getX ^MouseEvent event) (.getY ^MouseEvent event)]}))
                             (when (= (.getButton ^MouseEvent event) MouseEvent/BUTTON1)
                               (swap! stateA update-in [:Watney :route] conj [(.getX ^MouseEvent event) (.getY ^MouseEvent event)])))
                           (mouseEntered [_ event])
                           (mouseExited [_ event])
                           (mousePressed [_ event])
                           (mouseReleased [_ event]))))

    (doto panel
      (.setLayout layout)
      (.add code-panel)
      (.add canvas))

    (doto jframe
      (.setDefaultCloseOperation WindowConstants/DISPOSE_ON_CLOSE)
      (.setSize 2400 1600)
      (.setLocation 1300 200)
      (.add panel)
      (.setVisible true))

    (alter-var-root #'Watney.main/jframe (constantly jframe))
    (alter-var-root #'Watney.main/canvas (constantly canvas))
    (alter-var-root #'Watney.main/output-scroll (constantly output-scroll))
    (alter-var-root #'Watney.main/repl (constantly repl))
    (alter-var-root #'Watney.main/output (constantly output))
    (alter-var-root #'Watney.main/editor (constantly editor))
    (alter-var-root #'Watney.main/graphics (constantly (.getGraphics canvas)))

    (add-watch stateA :watch-fn
               (fn [ref wathc-key old-state new-state]

                 (clear-canvas)
                 (.setPaint graphics (Color. 237 211 175 200))
                 (.fillRect graphics 0 0 (.getWidth canvas) (.getHeight canvas))

                 (doseq [[k value] new-state]

                   (condp identical? (:shape value)

                     :Watney
                     (let [{:keys [^int x
                                   ^int y
                                   name
                                   ^int energy
                                   route]} value
                           body (Polygon. (int-array [x (+ x 15) (+ x 15) x]) (int-array [(+ y 10) (+ y 10) (+ y 45) (+ y 45)]) 4)]
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

                       (loop [route (cons [x y] route)
                              [ax ay] (first route)
                              [bx by] (second route)]
                         (when (and ax ay bx by)
                           (.setColor graphics Color/BLUE)
                           (.drawLine graphics ax ay bx by)
                           (recur (rest route) (first (rest route)) (second (rest route))))))

                     :martian
                     (let [{:keys [^int x ^int y name]} value
                           face (Polygon. (int-array [x (+ x 30) (+ x 18) (+ x 12)]) (int-array [(+ y 10) (+ y 10) (+ y 40) (+ y 40)]) 4)
                           left-eye (Ellipse2D$Double. (+ x 5) (+ y 15) 8 4)
                           right-eye (Ellipse2D$Double. (+ x 17) (+ y 15) 8 4)
                           nose (Polygon. (int-array [(+ x 13) (+ x 15) (+ x 17)]) (int-array [(+ y 25) (+ y 20) (+ y 25)]) 3)
                           mouth (Ellipse2D$Double. (+ x 12) (+ y 30) 8 2)]
                       (.setPaint graphics (Color. 3 165 106))
                       (.fill graphics face)
                       (.setPaint graphics Color/WHITE)
                       (.fill graphics left-eye)
                       (.fill graphics right-eye)
                       (.fill graphics nose)
                       (.fill graphics mouth))

                     :tower
                     (let [{:keys [^int x ^int y name]} value
                           shape (Polygon. (int-array [(- x 5) x (+ x 5)]) (int-array [(+ y 30) (+ y 10) (+ y 30)]) 3)]
                       (.setStroke graphics (BasicStroke. 4))
                       (.setColor graphics Color/DARK_GRAY)
                       (.draw graphics shape))

                     :metallic-insects-cloud
                     (let [{:keys [xs ys name]} value
                           shape (Polygon. (int-array xs) (int-array ys) (count xs))]
                       (.setStroke graphics (BasicStroke. 1))
                       (.setColor graphics Color/LIGHT_GRAY)
                       (.fill graphics shape))

                     (do nil)))

                 (let [[^int x ^int y] (:coordinate @stateA)]
                   (when (and x y)
                     (.setStroke graphics (BasicStroke. 1))
                     (.setColor graphics Color/BLACK)
                     (.drawString graphics (str [x y]) x y)
                     (.fill graphics (Ellipse2D$Double. x y 5 5))))))

    (go
      (<! (timeout 100))

      (let [Watney {:Watney {:name "Watney"
                             :shape :Watney
                             :x (+ 100 (rand-int 1200))
                             :y (+ 100 (rand-int 1400))
                             :route []
                             :energy 500}}
            martians (into {}
                           (comp
                            (map (fn [i]
                                   {:name (format "martian %s" i)
                                    :shape :martian
                                    :x (+ 100 (rand-int 1200))
                                    :y (+ 100 (rand-int 1400))}))
                            (map (fn [value] [(:name value) value])))
                           (range 0 10))
            towers  (into {}
                          (comp
                           (map (fn [i]
                                  {:name (format "tower %s" i)
                                   :shape :tower
                                   :x (+ 100 (rand-int 1200))
                                   :y (+ 100 (rand-int 1400))}))
                           (map (fn [value] [(:name value) value])))
                          (range 0 10))
            metallic-insects-clouds (into {}
                                          (comp
                                           (map (fn [i]
                                                  (let [x (+ 100 (rand-int 1200))
                                                        y (+ 100 (rand-int 1400))
                                                        width (+ 30 (rand-int 70))
                                                        height (+ 30 (rand-int 70))
                                                        gen-x (Pawny.generators/large-integer* {:min x :max (+ x width)})
                                                        gen-y (Pawny.generators/large-integer* {:min y :max (+ y height)})
                                                        n-points (+ 3 (rand-int 10))
                                                        xs (->
                                                            (Pawny.generators/vector-distinct gen-x {:num-elements n-points :max-tries 20})
                                                            (Pawny.generators/generate))
                                                        ys (->
                                                            (Pawny.generators/vector-distinct gen-y {:num-elements n-points :max-tries 20})
                                                            (Pawny.generators/generate)
                                                            (sort))]
                                                    {:name (format "metallic-insects-cloud %s" i)
                                                     :shape :metallic-insects-cloud
                                                     :xs xs
                                                     :ys ys})))
                                           (map (fn [value] [(:name value) value])))
                                          (range 0 10))]
        (swap! stateA merge
               Watney
               martians
               towers
               #_metallic-insects-clouds
               {:coordinate nil})


        (eval* '(list 'move))
        (eval* '(doc transmit))

        (let []
          (.setText editor (format
                            "
(go 
  (loop []
    (<! (timeout 1000))
    (when (can-move?)
      (move)
      (recur)
    )
  )
)
")))))
    nil))

(defn reload
  []
  (require
   '[Watney.main]
   :reload))

(defn -main
  [& args]
  (let []
    (reset! stateA {})

    (window)))

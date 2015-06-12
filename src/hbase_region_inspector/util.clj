(ns hbase-region-inspector.util
  (:require [clojure.string :as str]))

(defn compare-server-names
  "Comparator function for server names. Pads numbers with zeros"
  [left right]
  (let [fmt   (fn [s] (str/replace s #"[0-9]+" #(format "%04d" (Long/parseLong %))))
        left  (fmt left)
        right (fmt right)]
    (.compareTo left right)))

(defn rand-range
  "Returns a random number between min and max"
  [min max]
  (+ min (rand (- max min))))

(defn hsl->rgb
  "Converts HSL color to RGB color (#RRGGBB)"
  [h s l]
  (let [float->hex #(->> %
                         (* 256)
                         Math/floor
                         int
                         (format "%02x"))
        make-rgb #(apply str "#" (map float->hex %&))
        hue->rgb (fn [p q t]
                   (let [t (cond
                             (neg? t) (inc t)
                             (> t 1) (dec t)
                             :else t)]
                     (cond
                       (< t 1/6) (+ p (* (- q p) 6 t))
                       (< t 1/2) q
                       (< t 2/3) (+ p (* (- q p) (- 2/3 t) 6))
                       :well-then p)))]
    (if (zero? s)
      (make-rgb l l l)
      (let [q (if (< l 0.5)
                (* l (+ 1 s))
                (+ l s (- (* l s))))
            p (- (* 2 l) q)
            r (hue->rgb p q (+ h 1/3))
            g (hue->rgb p q h)
            b (hue->rgb p q (- h 1/3))]
        (make-rgb r g b)))))

(defn color-pair
  "Returns a pair of colors (BG and FG) suitable for progress-bars"
  []
  (let [h (rand)
        s (rand-range 0.2 0.5)
        l (rand-range 0.6 0.8)]
    [(hsl->rgb h s l)
     (hsl->rgb h s (- l 0.2))]))

(defn- log [type message]
  (println (format "%s: %s: %s" (java.util.Date.) type message)))
(defn info [message] (log "INFO" message))
(defn error [message] (log "ERROR" message))

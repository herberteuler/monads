(ns monads.examples.evaluator5
  (:require [monads.core :refer :all]
            [monads.state :as s]
            [monads.writer :as w]
            [monads.error :as error])
  (:use [monads.util :only [lift-m lift-m* guard]]))

(defn const [x]
  (if (symbol? x)
    (mdo state <- s/get-state
         (if (not (contains? state x))
           (lift (error/throw-error (str "no value for name: " x)))
           (return (get state x))))
    (return x)))

(declare run)

(defn calc [op x y msg]
  (mdo
   (lift (lift (w/tell [(str msg ": " x ", " y)])))
   (lift-m* op (run x) (run y))))

(defn decl [x y]
  (mdo v <- (run y)
       (s/modify #(assoc % x v))
       (lift (lift (w/tell [(str "decl " x)])))
       (return v)))

(defn run [op]
  (if (list? op)
    (case (second op)
      + (calc + (first op) (last op) "plus")
      - (calc - (first op) (last op) "minus")
      * (calc * (first op) (last op) "times")
      / (calc / (first op) (last op) "div")
      = (decl (first op) (last op)))
    (const op)))

(def table {'pi 3.141592 'e 2.718281 'deg 57.295779})

(defn evaluate [op]
  (s/run-state-t (s/t (error/t w/m)) (run op) table))
(ns monads.list
  (:require [monads.core :refer :all]))


(defn foldcat [f acc xs]
  (lazy-seq
   (if (not (seq xs))
     acc    
     (concat (f (first xs)) (foldcat f acc (rest xs))))))

;; list-t is not always a correct transformer. Omitted.
(defmonad list-m
  :return list
  :bind (fn [m f]
          (foldcat (comp (partial run-monad list-m) f)  '()  m))
  :monadplus {:mzero ()
              :mplus (fn [leftright]
                       (concat (run-monad list-m (first leftright))
                               (run-monad list-m (second leftright))))})

(def m list-m)

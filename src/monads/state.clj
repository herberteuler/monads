(ns monads.state
  (:require [monads.core :refer :all])
  (:use [monads.types :only [fst snd]]
        [monads.util :only [curryfn lazy-pair if-inner-return lift-m]])
  (:import [monads.types Returned Pair]))

(declare state-t)

(defn run-state-t [m computation initial-state]
  ((run-monad m computation) initial-state))

(defn- state-t* [inner]
  (let [i-return (:return inner)]
    (monad
     :inner inner
     :return (curryfn [x s] (i-return (Pair. x s)))
     :bind (fn [m f]
             (fn [s]
               (run-mdo inner
                        ^Pair p <- (m s)
                        let v = (fst p) s = (snd p)
                        (run-state-t (state-t inner)
                                     (f v) s))))
     :monadfail (when (:monadfail inner)
                  {:mfail (curryfn [str _] ((-> inner :monadfail :mfail) str))})
     :monadplus (when (:monadplus inner)
                  (let [i-plus (-> inner :monadplus :mplus)
                        i-zero (-> inner :monadplus :mzero)]
                    {:mzero (fn [_] i-zero)
                     :mplus (curryfn [leftright s]
                              (i-plus
                               (lazy-pair
                                (run-state-t (state-t inner) (first leftright) s)
                                (run-state-t (state-t inner) (second leftright) s))))}))
     :monadtrans {:lift (curryfn [m s]
                          (run-mdo inner
                                   v <- m
                                   (return (Pair. v s))))})))
(def state-t (memoize state-t*))

(defn state-return [x]
  (fn inner-state-return [s] (Pair. x s)))

(declare run-state)

(defmonad state-m
  :return (curryfn [x s] (Pair. x s))
  :bind (fn [m f]
          (fn [s]
            (let [^Pair p (m s)]
              (run-state (f (fst p)) (snd p))))))

(defn run-state [computation initial-state]
  ((run-monad state-m computation) initial-state))

(def get-state (Returned. (curryfn [m s]
                            (if-inner-return m
                              (i-return (Pair. s s))
                              (Pair. s s)))))

(def get-state (Returned. (curryfn [m s]
                            (if-let [i-return (-> m :inner :return)]
                              (i-return (Pair. s s))
                              (Pair. s s)))))
(defn put-state [v] (Returned. (curryfn [m s]
                                 (if-let [i-return (-> m :inner :return)]
                                   (i-return (Pair. nil v))
                                   (Pair. nil v)))))
(defn modify [f] (>>= get-state (comp put-state f)))

(def eval-state (comp fst run-state))
(def exec-state (comp snd run-state))
(defn exec-state-t [m comp initial-state]
  (run-monad (:inner m) (lift-m snd (run-state-t m comp initial-state))))
(defn eval-state-t [m comp initial-state]
  (run-monad (:inner m) (lift-m fst (run-state-t m comp initial-state))))

(def t state-t)
(def m state-m)

;;; example

;; ;; tree-numbering.
;; ;; Our trees: {:val int :left tree :right tree}, or nil
;; (defn node [v left right]
;;   {:val v :left left :right right})
;; (defn index-in-list [p lst]
;;   (second (first (filter (comp p first) (map vector lst (range))))))
;; (defn n-node [x table]
;;   (if-let [i (get table x)]
;;     [table i]
;;     (let [c (count table)]
;;       [(assoc table x c) c])))
;; (defn number-node [x]
;;   (mdo table <- get-state
;;        let [newtable newpos] = (n-node x table)
;;        (put-state newtable)
;;        (return newpos)))
;; (defn number-tree [{:keys [val left right] :as tree}]
;;   (if-not tree
;;     (return nil)
;;     (mdo num <- (number-node val)
;;          nt1 <- (number-tree left)
;;          nt2 <- (number-tree right)
;;          (return (node num nt1 nt2)))))
;; (defn num-tree [t]
;;   (eval-state (number-tree t) {}))

;; (defn tree [n]
;;   (reduce #(node %2 %1 %1) nil (range n)))
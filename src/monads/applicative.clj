(ns monads.applicative
  (:require [babbage.monoid :as m]
            [monads.types :as types]
            [monads.util :as u]
            [monads.core :as core]
            [clojure.algo.generic.functor :as f])
  (:import [monads.types Just Either Mplus Returned Bind Return Pair]))

(set! *warn-on-reflection* true)

(deftype Pure [f]
  Object
  (toString [this]
    (with-out-str (print f))))

(def pure ->Pure)

(defn cpure [arity f]
  (pure (u/curry arity f)))

(defprotocol Applicative
  (fapply [me f]))

(defn <*>
  [f & as]
  (reduce (fn [acc a] (fapply a acc)) f as))

(defn pure->mon [o]
  (types/if-instance Pure o
    (Return. (.f o))
    o))

(defn- fapply-mon [v f]
  (u/ap (pure->mon f) v))

(defn- fapply-lazyseq [me f]
  (if-let [fs (types/if-instance Pure f
                [(.f f)]
                (seq f))]
    (u/mcat (fn [f] (map f me)) fs)
    nil))

(deftype Const [t])

(defn get-const [^Const t] (.t t))

(defmethod f/fmap Const
  [f v] v)

(deftype Id [i])

(defn get-id [^Id i] (.i i))

(defmethod f/fmap Id
  [f ^Id v]
  (Id. (f (.i v))))

(deftype Fold [combine start finish])

(defmethod f/fmap Fold
  [f ^Fold fold]
  (Fold. (.combine fold) (.start fold) (comp f (.finish fold))))

(defn fold [^Fold f as]
  ((.finish f) (reduce (.combine f) (.start f) as)))

(extend-protocol Applicative

  Fold
  (fapply [me o]
    (let [[combinel startl finishl] (types/cond-instance o
                                        Pure [(fn [_ _] nil) nil (fn [_] (.f o))]
                                        Fold [(.combine o) (.start o) (.finish o)])
          [combiner startr finishr] [(.combine me) (.start me) (.finish me)]]
      (Fold. (fn [^Pair p a] (Pair. (combinel (.fst p) a) (combiner (.snd p) a)))
             (Pair. startl startr)
             (fn [^Pair p] ((finishl (.fst p)) (finishr (.snd p)))))))
  
  Just
  (fapply [me o]    
    (when-let [f (types/cond-instance o
                     Just (.v o)
                     Pure (.f o))]
      (types/just (f (.v me)))))

  Either
  (fapply [me o]
    (if-let [f (types/cond-instance o
                   Pure (.f o)
                   Either (types/either (constantly nil) identity o))]
      (if (types/right? me)
        (types/right (f (types/from-right me)))
        me)
      o))

  Return
  (fapply [me f] (fapply-mon me f))

  Bind
  (fapply [me f] (fapply-mon me f))

  Returned
  (fapply [me f] (fapply-mon me f))

  Mplus
  (fapply [me f] (fapply-mon me f))

  clojure.lang.IPersistentVector
  (fapply [me f] (if-let [fs (types/if-instance Pure f
                               [(.f f)]
                               (seq f))]
                   (vec (u/mcat (fn [f] (mapv f me)) fs))
                   []))

  clojure.lang.IPersistentList
  (fapply [me f] (fapply-lazyseq me f))

  clojure.lang.LazySeq
  (fapply [me f] (fapply-lazyseq me f))

  clojure.lang.PersistentVector$ChunkedSeq
  (fapply [me f] (fapply-lazyseq me f))

  nil
  (fapply [me f] me)

  Const
  (fapply [me f] (let [f (types/cond-instance f
                             Pure nil
                             Const (.t f))]
                   (m/<> f (.t me))))

  Id
  (fapply [me f] (if-let [f (types/cond-instance f
                                Pure (.f f)
                                Id (.i f))]
                   (Id. (f (.i me))))))

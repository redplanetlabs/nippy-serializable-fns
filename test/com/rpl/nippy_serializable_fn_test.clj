(ns com.rpl.nippy-serializable-fn-test
  (:require [clojure.test :refer :all]
            [com.rpl.nippy-serializable-fn :as serfn]
            [taoensso.nippy :as nippy]))

(serfn/clear-cache)

(def regular-fn-without-closure
  (fn unresolvable-name []
    (+ 1 2)))

(def regular-fn-with-closure
  (let [x 2]
    (fn unresolvable-name []
      (+ 1 2 x))))

(def Y 2)

(def regular-fn-with-constant
  (fn unresolvable-name []
    (+ 1 Y)))

(def regular-fn-with-constant-and-closure
  (let [x 2]
    (fn []
      (+ 1 x Y))))

(defn roundtrip [afn]
  (nippy/thaw (nippy/freeze afn))
  )

(defn do-roundtrip-test [afn expected & args]
  (is (= expected (apply (roundtrip afn) args)))
  )

(deftest roundtrip-regular-fn
  (do-roundtrip-test regular-fn-without-closure 3)
  (do-roundtrip-test regular-fn-with-closure 5)
  (do-roundtrip-test regular-fn-with-constant 3)
  (do-roundtrip-test regular-fn-with-constant-and-closure 5)
  )

(defn static-fn-without-closure []
  (+ 3 7))

(let [x 5]
  (defn static-fn-with-closure []
    (+ 3 7 x)))

(defn static-fn-with-constant []
  (+ 4 Y))

(let [x 9]
  (defn static-fn-with-constant-and-closure []
    (+ 4 x Y)))

(deftest roundtrip-static-fn
  (do-roundtrip-test static-fn-without-closure 10)
  (is (identical? static-fn-without-closure (roundtrip static-fn-without-closure)))
  (do-roundtrip-test static-fn-with-closure 15)
  (is (identical? static-fn-with-closure (roundtrip static-fn-with-closure)))
  (do-roundtrip-test static-fn-with-constant 6)
  (is (identical? static-fn-with-constant (roundtrip static-fn-with-constant)))
  (do-roundtrip-test static-fn-with-constant-and-closure 15)
  (is (identical? static-fn-with-constant-and-closure (roundtrip static-fn-with-constant-and-closure)))
  )

(deftest roundtrip-with-meta-fn
  (do-roundtrip-test (with-meta static-fn-without-closure {:my "meta"}) 10)
  (is (= {:my "meta"} (meta (roundtrip (with-meta static-fn-without-closure {:my "meta"})))))
  (do-roundtrip-test (with-meta regular-fn-without-closure {:my "meta"}) 3)
  (is (= {:my "meta"} (meta (roundtrip (with-meta regular-fn-without-closure {:my "meta"})))))
  (do-roundtrip-test (with-meta regular-fn-with-closure {:my "meta"}) 5)
  (is (= {:my "meta"} (meta (roundtrip (with-meta regular-fn-with-closure {:my "meta"})))))
  )

(defprotocol Foo
  (prot1 [this a])
  (prot2 [this a b]))

(deftest roundtrip-protocol-fn
  (is (identical? prot1 (roundtrip prot1)))
  (is (identical? prot2 (roundtrip prot2)))
  )

;; performance experiment code
(comment
 (def z 1)
 (defn adder [x] (+ x z))

 (defn mk-adder [y]
   (fn [x]
     (+ x y z)))

 (def some-adder (mk-adder 2))

 (def meta-fn (with-meta (fn [x] (inc x)) {:something "something-else"}))

 (defn mk-closes-over-bool [a-boolean]
   (fn [x]
     (if (and x a-boolean)
       (println "both truthy")
       (println "someone's not truthy!"))))

 (defn javaser-freeze [o]
   (let [bos (java.io.ByteArrayOutputStream.)
         oos (java.io.ObjectOutputStream. bos)]
     (.writeObject oos o)
     (.close oos)
     (.toByteArray bos)))

 (defn javaser-thaw [bytes]
   (let [bis (java.io.ByteArrayInputStream. bytes)
         ois (java.io.ObjectInputStream. bis)]
     (.readObject ois)))

 (defn javaser-freeze-stream [aseq]
   (let [bos (java.io.ByteArrayOutputStream.)
         oos (java.io.ObjectOutputStream. bos)]
     (doseq [o aseq]
       (.writeObject oos o))
     (.close oos)
     (.toByteArray bos)))

 (let [reps 10000000]
   (time (dotimes [n reps]
                  (nippy/fast-freeze some-adder))))

 (let [reps 10000000]
   (time (dotimes [n reps]
                  (javaser-freeze some-adder))))

 (let [reps 10000000
       aseq (vec (repeatedly reps #(identity some-adder)))]
   (time
    (javaser-freeze-stream aseq)))

 (defrecord FnAnalog [y])

 (let [reps 10000000
       rec (->FnAnalog 5)]
   (time (dotimes [n reps]
                  (nippy/fast-freeze rec))))

 (let [reps 10000000
       bytes (nippy/freeze some-adder)]
   (time (dotimes [n reps]
                  (nippy/thaw bytes))))

)

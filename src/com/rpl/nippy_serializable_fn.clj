(ns com.rpl.nippy-serializable-fn
  "This namespace custom freeze/thaw implementations that allow Clojure fns to
be serialized with all the standard Nippy freeze/thaw fns.

Only a fn's internal class name and the objects in the closure are actually
serialized. No code or bytecode is serialized. Both the freezing process and the
thawing process must be running the same code, or the thaw operation can have
undefined behavior.

A custom serializer / deserializer for each frozen/thawed fn type is generated
upon first use, and then cached for future access, making it very efficient.
(No reflection is required during steady state operation.) The cache can be
cleared via the `clear-cache` fn; you typically only need to do this during
interactive development.

To use this extension, just define fns like usual and then freeze instances to bytes:

`(let [d 10
       my-fn (fn [a b c] (+ a b c d))]
   (nippy/freeze! my-fn) ; => bytes
   )`

The same instances can be thawed again later, and will reconstruct its closure as expected:

`(let [d 10
       my-fn (fn [a b c] (+ a b c d))
       frozen-fn-bytes (nippy/freeze! my-fn)
       thawed-fn (nippy/thaw! frozen-fn-bytes)]
    (thawed-fn 1 2 3); => 16
   )`

Be careful about freezing fns that have 'big' stuff in their closure! All
referenced objects will be recursively serialized. If an un-freezeable object is
encountered, then it will cause an error.
"
  (:require [taoensso.nippy :as nippy]
            )
  (:import [clojure.lang AFunction]
           [java.lang.reflect Field Modifier]
           [java.lang.invoke MethodHandle MethodHandles]
           [java.util List Collections])
  )

(defonce method-handle-lookup (. MethodHandles (lookup)))

(defonce ^:private serializer-cache (atom {}))
(defonce ^:private deserializer-cache (atom {}))

(defn- make-qualified [s]
  (if (clojure.string/includes? s "/")
    s
    (clojure.string/replace s #"\.([^.]*)$" "/$1"))
  )


(defn resolve-fn-var
  "When a fn is created via defn, the name of the fn class and the var that holds
   the fn instance are related and can be determined. In most cases, they are an
   almost exact match (modulo munging), except for:
    * when the fn has a closure, it'll have a gensym'd number at the end
    * when fn is evaluated in the repl, it'll include more or more '$eval' name
      segments that are not reflected in the var name
    * when the fn is a protocol method the class name must be retrieved via the
      methodImplCache"
  [v]
  (let [cls  ^Class (class v)
        vsym (symbol (clojure.lang.Compiler/demunge (.getName cls)))
        found-var (or (resolve vsym)
                      (some-> ^AFunction v
                              (.__methodImplCache)
                              (.sym)
                              resolve)
                      ;; this case handles functions like clojure.core/first which
                      ;; has a class name like clojure.core$first__5369
                      ;; the demunge converts __ in class name to --
                      (-> cls
                          .getName
                          ;; remove trailing instance garbage
                          (clojure.string/replace #"__\d+$" "")
                          ;; remove the "evalXXXX" that pops up if you've got a closure
                          (clojure.string/replace #"\$eval\d+\$" "\\$")
                          clojure.lang.Compiler/demunge
                          make-qualified
                          symbol
                          resolve
                          )
                      )
        ]
    (if (and found-var (identical? (var-get found-var) v))
      found-var
      )))

(defn clear-cache
  "Clear all cached generated serializer and deserializer fns. You typically
only need to do this if you've redefined a fn to include new vars in its closure
during interactive development."
  []
  (reset! serializer-cache {})
  (reset! deserializer-cache {}))

(defn- fields-to-serialize [klass]
  (let [fields (->> ^Class klass
                    .getDeclaredFields
                    (filter
                      #(not (Modifier/isStatic (.getModifiers ^Field %)))))]
    (doseq [f fields]
      (.setAccessible ^Field f true))
    fields
  ))

;; this helps us generate a serialization fn for a fn that has a closure, that
;; is, it references non-argument stuff in its body. to avoid doing expensive
;; reflection at execution time, we do the reflection once, then embed use of a
;; MethodHandle that can access the fields that end up on the class for the fn.
;; MethodHandles are magic and basically have the same performance as actually
;; accessing a field natively.
(defn- mk-serializer-for-anon-fn [afn]
  (let [fn-class ^Class (class afn)
        class-name (.getName fn-class)
        klass-sym (gensym "klass")
        afn-sym (gensym "afn")
        data-output-sym (gensym "data-output")
        fields (fields-to-serialize fn-class)
        accessor-syms (repeatedly (count fields) #(gensym "method-handle"))
        accessor-lookups (apply concat
                           (mapv (fn [field accesor-sym]
                                   [accesor-sym
                                    `(let [field# (.getDeclaredField ~klass-sym ~(.getName ^Field field))
                                           _# (.setAccessible field# true)]
                                       (.unreflectGetter ^java.lang.invoke.MethodHandles$Lookup method-handle-lookup field#))])
                                 fields accessor-syms))

        freeze-statements
        (mapv (fn [accessor-sym]
          `(nippy/freeze-to-out!
             ~data-output-sym
             (.invokeWithArguments
               ^MethodHandle ~accessor-sym
               ^List (. Collections (singletonList ~afn-sym)))))
            accessor-syms)]
    (eval `(let [~klass-sym (clojure.lang.RT/classForName ~class-name)
                 ~@accessor-lookups]
             (fn [~afn-sym ~data-output-sym]
               (nippy/freeze-to-out! ~data-output-sym ~class-name)
               ~@freeze-statements)))))

(defn- mk-serializer-for-static-fn [fn-symbol]
  (fn [afn data-output]
    (nippy/freeze-to-out! data-output fn-symbol)))

(defn- outer-class-instance
  [^Object v]
  (let [^Field f (.getDeclaredField clojure.lang.AFunction$1 "this$0")]
    (.setAccessible f true)
    (.get f v)))

(defn- afunction-inner-class? [^Class cls]
  (= clojure.lang.AFunction$1 cls))

(defn- mk-serializer-for-fn [afn]
  (let [rv (resolve-fn-var afn)]
    (if rv
      (mk-serializer-for-static-fn (symbol rv))
      (mk-serializer-for-anon-fn afn)))
  )

(defn- serializer-for-fn [afn]
  (if-let [serializer (get @serializer-cache (class afn))]
    serializer
    (let [serializer (mk-serializer-for-fn afn)]
      (swap! serializer-cache assoc (class afn) serializer)
      serializer)))

(nippy/extend-freeze
 clojure.lang.AFn ::afn
 [afn data-output]
 (let [serializer (serializer-for-fn afn)]
   (serializer afn data-output))
 )

;; code generating helper fn. we really want to avoid doing sequence traversal to
;; get good performance, so this helps us generate static code that does the thaws
;; and assigns into the right position in an array for use in constructor call.
(defn- gen-read-n-fields [n]
  (let [array-arg-sym (gensym "array")
        data-input-sym (gensym "data-input")
        thaws (mapv (fn [x] `(aset ~array-arg-sym ~x (nippy/thaw-from-in! ~data-input-sym)))
                    (range 0 n))]
    `(fn [~data-input-sym]
       (let [~array-arg-sym ^"objects" (make-array Object ~n)]
         ~@thaws
         ~array-arg-sym)))
  )

(defn- mk-deserializer-for-anon-fn [class-name]
  (let [klass (clojure.lang.RT/classForName class-name)
        fields (fields-to-serialize ^Class klass)
        read-fields-into-array (eval (gen-read-n-fields (count fields)))
        field-types (mapv #(.getType ^Field %) fields)
        constructor (.getConstructor ^Class klass
                                     (into-array Class field-types))]
   (fn [data-input]
     (.newInstance constructor (read-fields-into-array data-input))))
  )

(defn- deserializer-for-fn-class [class-name]
  (if-let [deserializer (get @deserializer-cache class-name)]
    deserializer
    (let [deserializer (mk-deserializer-for-anon-fn class-name)]
      (swap! deserializer-cache assoc class-name deserializer)
      deserializer)))

(defn- thaw-anon-fn [class-name data-input]
  ((deserializer-for-fn-class class-name) data-input))

(defn- thaw-static-fn [var-sym]
  (require (symbol (namespace var-sym)))
  (var-get (resolve var-sym)))

;; these extend-thaw / extend-freeze blocks are what install the custom handlers.
(nippy/extend-thaw
 ::afn
 [data-input]
 (let [class-name-or-symbol (nippy/thaw-from-in! data-input)]
   (if (symbol? class-name-or-symbol)
     (thaw-static-fn class-name-or-symbol)
     (thaw-anon-fn class-name-or-symbol data-input))))

(nippy/extend-freeze
 clojure.lang.AFunction$1 ::afn-inner
 [afn data-output]
 (let [outer-class-instance (outer-class-instance afn)]
   (nippy/freeze-to-out! data-output (meta afn))
   (nippy/freeze-to-out! data-output outer-class-instance))
 )

(nippy/extend-thaw
 ::afn-inner
 [data-input]
 (let [meta-map (nippy/thaw-from-in! data-input)
       fn-instance (nippy/thaw-from-in! data-input)]
   (with-meta fn-instance meta-map)))

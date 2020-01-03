(defproject rpl/nippy-serializable-fns 0.1.0
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/nippy "2.14.0"]]
  :test-paths ["test"]
  :plugins [[lein-exec "0.3.7"]]
  :profiles {:dev {:dependencies
                   [[org.clojure/test.check "0.9.0"]
                    [org.clojure/spec.alpha "0.2.176"]]}
             :check {:warn-on-reflection true}
             :nrepl {:lein-tools-deps/config {:resolve-aliases [:nrepl]}}
             }
  )

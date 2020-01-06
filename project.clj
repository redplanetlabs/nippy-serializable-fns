(def VERSION (.trim (slurp "VERSION")))

(defproject com.rpl/nippy-serializable-fns VERSION
  :description "An extension for Nippy that allows freezing and thawing Clojure fns."
  :url "https://github.com/redplanetlabs/nippy_serializable_fns"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/nippy "2.14.0"]]
  :jvm-opts [;; this prevents JVM from doing optimizations which can
             ;; remove stack traces from NPE and other exceptions
             "-XX:-OmitStackTraceInFastThrow"
             ;;             "-XX:MaxInlineSize=0"
             ;; this is needed so that clojure platform doesn't
             ;; get a stackoverflow during tests
             "-Xss4m"
             ;;             "-verbose:gc" "-XX:+PrintGCDetails"
             ;;             "-Dclojure.compiler.direct-linking=true"
             ;;             "-agentpath:/Applications/YourKit-Java-Profiler-2019.1.app/Contents/Resources/bin/mac/libyjpagent.jnilib"
             ]
  :test-paths ["test"]
  :plugins [[lein-exec "0.3.7"]
            ;; override the tools.deps.alpha version from lein-tools-deps
            [org.clojure/tools.deps.alpha "0.7.549"
             :hooks false :middleware false]
            ]
  :profiles {:dev {:dependencies
                   [[org.clojure/test.check "0.9.0"]
                    [org.clojure/spec.alpha "0.2.176"]]}
             :check {:warn-on-reflection true}
             :nrepl {:lein-tools-deps/config {:resolve-aliases [:nrepl]}}
             }
  )

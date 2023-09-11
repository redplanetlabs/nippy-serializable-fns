# nippy-serializable-fns
[![Clojars Project](https://img.shields.io/clojars/v/com.rpl/nippy-serializable-fns.svg)](https://clojars.org/com.rpl/nippy-serializable-fns)

A simple Clojure library that extends Nippy to allow freezing and thawing of Clojure functions. See [our blog post](https://tech.redplanetlabs.com/?p=124) for more discussion of this project and its implementation.

## Usage

Just add a dependency on the [Clojars package](https://clojars.org/com.rpl/nippy-serializable-fns), require the `com.rpl.nippy-serializable-fn` namespace, and then use `nippy/freeze!` and `nippy/thaw!` as usual. Clojure functions will effectively be serialized as the fn name plus any values captured in the fn's closure. Note that no code is serialized, so both the freezing process and the thawing process must run the same code version!

## License

Copyright © 2019 - 2023 Red Planet Labs Inc.

Distributed under the Apache Software License version 2.0

# nippy_serializable_fns
Nippy extension to add ability to freeze and thaw Clojure fns. Just require the namespace, and then use `nippy/freeze!` and `nippy/thaw!` as usual. Clojure fns will effectively be serialized as the fn name plus any values captured in the fn's closure. Note that no code is serialized, so both the freezing process and the thawing process must run the same code version!

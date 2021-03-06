#!/bin/bash

repl(){
  clj \
    -J-Dclojure.core.async.pool-size=1 \
    -X:repl Ripley.core/process \
    :main-ns Watney.main
}

main(){
  clojure \
    -J-Dclojure.core.async.pool-size=1 \
    -M -m Watney.main
}

uberjar(){

  clojure \
    -X:identicon Zazu.core/process \
    :word '"Watney"' \
    :filename '"out/identicon/icon.png"' \
    :size 256

  rm -rf out/*.jar
  clojure \
    -X:uberjar Genie.core/process \
    :main-ns Watney.main \
    :filename "\"out/Watney-$(git rev-parse --short HEAD).jar\"" \
    :paths '["src" "out/identicon"]'
}

release(){
  uberjar
}

"$@"
#!/bin/bash

repl(){
  clj \
    -J-Dclojure.core.async.pool-size=1 \
    -X:repl ripley.core/process \
    :main-ns rover.main
}

main(){
  clojure \
    -J-Dclojure.core.async.pool-size=1 \
    -M -m rover.main
}

uberjar(){

  clojure \
    -X:identicon zazu.core/process \
    :word '"rover"' \
    :filename '"out/identicon/icon.png"' \
    :size 256

  clojure \
    -X:uberjar genie.core/process \
    :main-ns rover.main \
    :filename '"out/rover.jar"' \
    :paths '["src" "out/identicon"]'
}

release(){
  uberjar
}

"$@"
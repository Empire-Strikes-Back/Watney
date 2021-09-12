#!/bin/bash

repl(){
  clj \
    -J-Dclojure.core.async.pool-size=1 \
    -X:repl Ripley.core/process \
    :main-ns Rover.main
}

main(){
  clojure \
    -J-Dclojure.core.async.pool-size=1 \
    -M -m Rover.main
}

uberjar(){

  clojure \
    -X:identicon Zazu.core/process \
    :word '"Rover"' \
    :filename '"out/identicon/icon.png"' \
    :size 256

  clojure \
    -X:uberjar Genie.core/process \
    :main-ns Rover.main \
    :filename '"out/Rover.jar"' \
    :paths '["src" "out/identicon"]'
}

release(){
  uberjar
}

"$@"
#!/bin/bash

repl(){
  clj -M:repl
}

main(){
  clojure -M:main
}

"$@"
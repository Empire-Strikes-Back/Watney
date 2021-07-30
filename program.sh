#!/bin/bash

repl(){
  clj -M:repl
}

main(){
  clojure -M:main
}

push(){
  ORIGIN=$(git remote get-url origin)
  rm -rf .git
  git init -b main
  git remote add origin $ORIGIN
  git config --local include.path ../.gitconfig
  git add .
  git commit -m "i am rovers program"
  git push -f -u origin main
}

"$@"
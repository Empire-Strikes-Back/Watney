#!/bin/bash

repl(){
  clj \
    -X:repl deps-repl.core/process \
    :main-ns rovers.main \
    :port 7788 \
    :host '"0.0.0.0"' \
    :repl? true \
    :nrepl? false
}

main(){
  clojure \
    -J-Dclojure.core.async.pool-size=1 \
    -J-Dclojure.compiler.direct-linking=false \
    -M -m rovers.main
}

uberjar(){
  clj \
    -X:uberjar genie.core/process \
    :uberjar-name out/rovers.standalone.jar \
    :main-ns rovers.main
  mkdir -p out/jpackage-input
  mv out/rovers.standalone.jar out/jpackage-input/
}

j-package(){
  OS=${1:?"Need OS type (windows/linux/mac)"}

  echo "Starting compilation..."

  if [ "$OS" == "windows" ]; then
    J_ARG="--win-menu --win-dir-chooser --win-shortcut"
          
  elif [ "$OS" == "linux" ]; then
      J_ARG="--linux-shortcut"
  else
      J_ARG=""
  fi

  jpackage \
    --input out/jpackage-input \
    --dest out \
    --main-jar rovers.standalone.jar \
    --name "rovers" \
    --main-class clojure.main \
    --arguments -m \
    --arguments rovers.main \
    --app-version "1" \
    $J_ARG
}

"$@"
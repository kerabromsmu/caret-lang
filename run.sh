#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac --release 21 -d out $(find src/main/java -name '*.java')
if [[ $# -eq 0 ]]; then
  java -cp out caretlang.Main examples/demo.caret
else
  java -cp out caretlang.Main "$@"
fi

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac --release 21 -d out $(find src/main/java -name '*.java')
java -cp out caretlang.Main "${1:-examples/demo.caret}"

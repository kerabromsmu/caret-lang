#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew --quiet installDist
launcher="build/install/caret-lang-prototype/bin/caret-lang-prototype"
if [[ $# -eq 0 ]]; then
  exec "$launcher" examples/demo.caret
fi
exec "$launcher" "$@"

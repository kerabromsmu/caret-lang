#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew --quiet installDist
exec build/install/caret-lang-prototype/bin/caret-lang-prototype

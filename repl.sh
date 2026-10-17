#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew --quiet installDist
exec build/install/caret/bin/caret

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

expect_valid() {
  scripts/validate-version.sh "$1" "$2" >/dev/null
}

expect_invalid() {
  if scripts/validate-version.sh "$1" "$2" >/dev/null 2>&1; then
    printf 'Expected invalid version transition succeeded: %s -> %s\n' "$1" "$2" >&2
    exit 1
  fi
}

expect_valid 0.0.0 0.1.0
expect_valid 0.1.0 0.1.1
expect_valid 0.1.9 0.2.0
expect_valid 0.9.4 1.0.0

expect_invalid 0.1.0 0.1.0
expect_invalid 0.1.0 0.1.2
expect_invalid 0.1.3 0.2.1
expect_invalid 0.1.3 1.1.0
expect_invalid 1.2.3 0.2.4
expect_invalid 1.2.3 1.02.4
expect_invalid 1.2.3 1.2

printf 'Version policy tests passed.\n'

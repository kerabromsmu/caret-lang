#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <caret-distribution.zip|caret-distribution.tar>\n' "$0" >&2
  exit 2
fi

archive=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
smoke_tmp=$(mktemp -d /tmp/caret-distribution-smoke.XXXXXX)
trap 'rm -rf -- "$smoke_tmp"' EXIT

case $archive in
  *.zip) unzip -q "$archive" -d "$smoke_tmp" ;;
  *.tar) tar -xf "$archive" -C "$smoke_tmp" ;;
  *)
    printf 'Unsupported distribution archive: %s\n' "$archive" >&2
    exit 2
    ;;
esac

distribution_root=$(find "$smoke_tmp" -mindepth 1 -maxdepth 1 -type d -print -quit)
if [[ -z $distribution_root ]]; then
  printf 'Distribution has no root directory: %s\n' "$archive" >&2
  exit 1
fi

for required in README.md EMBEDDING.md LICENSE NOTICE examples bin/caret bin/caret.bat; do
  if [[ ! -e "$distribution_root/$required" ]]; then
    printf 'Distribution is missing %s: %s\n' "$required" "$archive" >&2
    exit 1
  fi
done

launcher="$distribution_root/bin/caret"
"$launcher" "$distribution_root/examples/features/implemented_features.caret" \
  > "$smoke_tmp/program.out"
diff -u "$distribution_root/examples/features/implemented_features.expected" \
  "$smoke_tmp/program.out"

"$launcher" test "$distribution_root/examples/testing.caret" > "$smoke_tmp/test.out"
grep -F 'Summary: 3 tests, 3 passed, 0 failed' "$smoke_tmp/test.out" >/dev/null

mkdir "$smoke_tmp/home"
printf 'exit\n' | JAVA_OPTS="-Duser.home=$smoke_tmp/home" "$launcher" > "$smoke_tmp/repl.out"
grep -F 'Caret prototype REPL.' "$smoke_tmp/repl.out" >/dev/null

printf 'Distribution smoke test passed: %s\n' "$archive"

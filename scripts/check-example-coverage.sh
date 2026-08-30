#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

failures=0
implemented=0

while IFS='|' read -r _ requirement _ status _ examples _; do
  requirement=$(printf '%s' "$requirement" | xargs)
  status=$(printf '%s' "$status" | xargs)
  [[ "$status" == implemented ]] || continue
  ((implemented += 1))

  references=$(printf '%s' "$examples" | grep -oE 'examples/[A-Za-z0-9_./-]+\.caret' || true)
  if [[ -z "$references" ]]; then
    printf 'Implemented conformance row has no runnable Caret example: %s\n' "$requirement" >&2
    failures=1
    continue
  fi
  while IFS= read -r reference; do
    if [[ ! -f "$reference" ]]; then
      printf 'Conformance row %s references a missing example: %s\n' "$requirement" "$reference" >&2
      failures=1
    elif ! grep -Fq "$reference" test.sh; then
      printf 'Conformance row %s references an example not exercised by test.sh: %s\n' \
        "$requirement" "$reference" >&2
      failures=1
    fi
  done <<< "$references"
done < CONFORMANCE.md

while IFS= read -r -d '' source; do
  source=${source#./}
  if ! grep -Fq "$source" test.sh; then
    printf 'Caret fixture is not exercised by test.sh: %s\n' "$source" >&2
    failures=1
  fi
  expected=${source%.caret}.expected
  if [[ ! -f "$expected" ]]; then
    printf 'Caret fixture has no expected output or diagnostic: %s\n' "$source" >&2
    failures=1
  fi
done < <(find examples/features examples/errors -type f -name '*.caret' -print0)

if ((failures != 0)); then
  exit 1
fi

printf 'Example coverage tests passed: %s implemented requirements.\n' "$implemented"

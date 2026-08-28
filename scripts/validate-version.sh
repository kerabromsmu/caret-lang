#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <previous-version> <new-version>\n' "$0" >&2
  exit 2
fi

previous=$1
current=$2
semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

parse_version() {
  local value=$1
  local label=$2
  if [[ ! $value =~ $semver ]]; then
    printf '%s version must use MAJOR.MINOR.UPDATE without leading zeroes: %s\n' "$label" "$value" >&2
    exit 1
  fi
  VERSION_MAJOR=${BASH_REMATCH[1]}
  VERSION_MINOR=${BASH_REMATCH[2]}
  VERSION_UPDATE=${BASH_REMATCH[3]}
}

parse_version "$previous" Previous
previous_major=$VERSION_MAJOR
previous_minor=$VERSION_MINOR
previous_update=$VERSION_UPDATE

parse_version "$current" Current
current_major=$VERSION_MAJOR
current_minor=$VERSION_MINOR
current_update=$VERSION_UPDATE

if (( current_major == previous_major &&
      current_minor == previous_minor &&
      current_update == previous_update + 1 )); then
  printf 'Valid update release: %s -> %s\n' "$previous" "$current"
  exit 0
fi

if (( current_major == previous_major &&
      current_minor == previous_minor + 1 &&
      current_update == 0 )); then
  printf 'Valid phase release: %s -> %s\n' "$previous" "$current"
  exit 0
fi

if (( current_major == previous_major + 1 &&
      current_minor == 0 && current_update == 0 )); then
  printf 'Valid owner-authorized major release: %s -> %s\n' "$previous" "$current"
  exit 0
fi

printf 'Invalid release transition: %s -> %s\n' "$previous" "$current" >&2
printf 'Increment UPDATE by one, increment MINOR by one and reset UPDATE, or increment MAJOR by one and reset both lower components.\n' >&2
exit 1

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <caret-java-sdk.zip>\n' "$0" >&2
  exit 2
fi

archive=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
sdk_tmp=$(mktemp -d /tmp/caret-embedding-sdk-smoke.XXXXXX)
trap 'rm -rf -- "$sdk_tmp"' EXIT

case $archive in
  *.zip) unzip -q "$archive" -d "$sdk_tmp" ;;
  *)
    printf 'Unsupported embedding SDK archive: %s\n' "$archive" >&2
    exit 2
    ;;
esac

sdk_root=$(find "$sdk_tmp" -mindepth 1 -maxdepth 1 -type d -print -quit)
if [[ -z $sdk_root ]]; then
  printf 'Embedding SDK has no root directory: %s\n' "$archive" >&2
  exit 1
fi

for required in README.md LICENSE NOTICE examples/EmbeddingExample.java \
    examples/embedding.caret docs/javadoc/index.html; do
  if [[ ! -e "$sdk_root/$required" ]]; then
    printf 'Embedding SDK is missing %s: %s\n' "$required" "$archive" >&2
    exit 1
  fi
done

embedding_jar=$(find "$sdk_root/lib" -maxdepth 1 -type f \
  -name 'caret-embedding-*.jar' -print -quit)
if [[ -z $embedding_jar ]]; then
  printf 'Embedding SDK has no caret-embedding JAR: %s\n' "$archive" >&2
  exit 1
fi

mkdir "$sdk_tmp/classes"
javac -cp "$sdk_root/lib/*" -d "$sdk_tmp/classes" "$sdk_root/examples/EmbeddingExample.java"
java -cp "$sdk_tmp/classes:$sdk_root/lib/*" caretlang.examples.EmbeddingExample \
  "$sdk_root/examples/embedding.caret" > "$sdk_tmp/output.txt"
printf 'Hello, Java\n' > "$sdk_tmp/expected.txt"
diff -u "$sdk_tmp/expected.txt" "$sdk_tmp/output.txt"

printf 'Embedding SDK smoke test passed: %s\n' "$archive"

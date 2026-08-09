#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
CARET_TEST_TMP=$(mktemp -d /tmp/caret-tests.XXXXXX)
trap 'rm -rf -- "$CARET_TEST_TMP"' EXIT
./run.sh examples/demo.caret > "$CARET_TEST_TMP/output.txt"
cat > "$CARET_TEST_TMP/expected.txt" <<'EXPECTED'
true
false
A
10
~
~
?
~
10
Scope
name,count
1
EXPECTED
diff -u "$CARET_TEST_TMP/expected.txt" "$CARET_TEST_TMP/output.txt"

./run.sh examples/implemented_features.caret > "$CARET_TEST_TMP/implemented-features-output.txt"
diff -u examples/implemented_features.expected "$CARET_TEST_TMP/implemented-features-output.txt"

./run.sh test examples/testing.caret > "$CARET_TEST_TMP/testing-output.txt"
cat > "$CARET_TEST_TMP/testing-expected.txt" <<'EXPECTED'
PASS: addition produces the expected value
PASS: null remains distinct from missing
PASS: sequences compare structurally
Summary: 3 tests, 3 passed, 0 failed
EXPECTED
diff -u "$CARET_TEST_TMP/testing-expected.txt" "$CARET_TEST_TMP/testing-output.txt"

./run.sh test examples/implemented_features_test.caret \
  > "$CARET_TEST_TMP/implemented-features-test-output.txt"

cat > "$CARET_TEST_TMP/language.caret" <<'CARET'
add a b = a + b
mul a b = a * b

between low value high =
  value >= low and value <= high

pair a b =
  ^first = a
  ^second = b

factory =
  hidden = 40 + 2
  ^answer = hidden
  ^nothing = ?

print (1 + 2 * 3)
print (mul (add 1 2) 3)
print (not false and true)
print (true or unknownName)
print (false and unknownName)
print (true & "yes" ! unknownName)
print (false & unknownName ! "no")
print (false & unknownName)

inside = between 0 _ 10
print (inside 7)
print (inside 11)
makePair = pair _ _
p = makePair "left" "right"
print p.first
print p.second

made = factory
print made.answer
print made.nothing
print made.absent~
field = #answer
print made[field]
print made["absent"]~

print (@42).kind
print (@inside).kind
print (@inside).remaining
print (@made).kind
print (@made).size
print (@made).names
CARET

./run.sh "$CARET_TEST_TMP/language.caret" > "$CARET_TEST_TMP/language-output.txt"
cat > "$CARET_TEST_TMP/language-expected.txt" <<'EXPECTED'
7
9
true
true
false
yes
no
~
true
false
left
right
42
?
~
42
~
Number
Function
1
Scope
2
answer,nothing
EXPECTED
diff -u "$CARET_TEST_TMP/language-expected.txt" "$CARET_TEST_TMP/language-output.txt"

cat > "$CARET_TEST_TMP/required-field.caret" <<'CARET'
make =
  ^present = true
value = make
print value.absent
CARET
if ./run.sh "$CARET_TEST_TMP/required-field.caret" > "$CARET_TEST_TMP/required-field.out" 2>&1; then
  printf 'Required missing field unexpectedly succeeded.\n' >&2
  exit 1
fi
grep -F 'Scope has no exported binding: absent' "$CARET_TEST_TMP/required-field.out" >/dev/null
printf 'All prototype tests passed.\n'

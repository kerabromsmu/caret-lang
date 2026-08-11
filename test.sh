#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
CARET_TEST_TMP=$(mktemp -d /tmp/caret-tests.XXXXXX)
trap 'rm -rf -- "$CARET_TEST_TMP"' EXIT
./gradlew --quiet installDist
CARET_LAUNCHER=build/install/caret-lang-prototype/bin/caret-lang-prototype

expect_failure() {
  local source_file=$1
  local expected_text=$2
  local output_file
  output_file="$CARET_TEST_TMP/$(basename "$source_file").out"
  if "$CARET_LAUNCHER" "$source_file" > "$output_file" 2>&1; then
    printf 'Expected failure succeeded: %s\n' "$source_file" >&2
    exit 1
  fi
  grep -F "$expected_text" "$output_file" >/dev/null
  if grep -F 'Exception' "$output_file" >/dev/null; then
    printf 'Java exception leaked from: %s\n' "$source_file" >&2
    exit 1
  fi
}
"$CARET_LAUNCHER" examples/demo.caret > "$CARET_TEST_TMP/output.txt"
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

"$CARET_LAUNCHER" examples/implemented_features.caret > "$CARET_TEST_TMP/implemented-features-output.txt"
diff -u examples/implemented_features.expected "$CARET_TEST_TMP/implemented-features-output.txt"

"$CARET_LAUNCHER" test examples/testing.caret > "$CARET_TEST_TMP/testing-output.txt"
cat > "$CARET_TEST_TMP/testing-expected.txt" <<'EXPECTED'
PASS: addition produces the expected value
PASS: null remains distinct from missing
PASS: sequences compare structurally
Summary: 3 tests, 3 passed, 0 failed
EXPECTED
diff -u "$CARET_TEST_TMP/testing-expected.txt" "$CARET_TEST_TMP/testing-output.txt"

"$CARET_LAUNCHER" test examples/implemented_features_test.caret \
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
field = "answer"
print made[field]
print made["absent"]~

print (@42).kind
print (@inside).kind
print (@inside).remaining
print (@made).kind
print (@made).size
print (@made).names
CARET

"$CARET_LAUNCHER" "$CARET_TEST_TMP/language.caret" > "$CARET_TEST_TMP/language-output.txt"
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

expect_failure examples/errors/duplicate_definition.caret 'Line 2, column 1: Duplicate definition: value'
grep -F 'Note: Line 1, column 1: First definition of value' \
  "$CARET_TEST_TMP/duplicate_definition.caret.out" >/dev/null
expect_failure examples/errors/reserved_binding.caret 'Line 1, column 1: Reserved spelling cannot be used as a binding name: true'
expect_failure examples/errors/read_before_initialization.caret 'Line 1, column 9: Binding read before initialization: second'
expect_failure examples/errors/unknown_name.caret 'Line 1, column 7: Unknown name: absent'
expect_failure examples/errors/required_missing_field.caret 'Line 5, column 7: Scope has no exported binding: absent'
expect_failure examples/errors/invalid_dynamic_key.caret 'Line 5, column 7: Dynamic field name must be a string'
expect_failure examples/errors/division_by_zero.caret 'Line 1, column 11: Division by zero'
expect_failure examples/errors/remainder_by_zero.caret 'Line 1, column 11: Division by zero'
expect_failure examples/errors/non_finite_result.caret 'Line 1, column 7: Numeric result is not finite'
expect_failure examples/errors/invalid_escape.caret 'Line 1, column 17: Unknown string escape: \q'
expect_failure examples/errors/mixed_holes.caret 'Line 2, column 11: Cannot mix numbered and unnumbered holes'
expect_failure examples/errors/callable_equality.caret 'Line 2, column 7: Callable values cannot be compared for equality'
expect_failure examples/errors/non_callable_infix.caret 'Line 2, column 9: Named infix target is not callable: value'
expect_failure examples/errors/invalid_infix_arity.caret 'Line 2, column 9: Named infix function must take exactly two arguments: identity'
expect_failure examples/errors/non_callable_composition.caret 'Line 3, column 12: Composition left operand must be a callable requiring at least one argument'
expect_failure examples/errors/invalid_composition_arity.caret 'Line 3, column 24: Composition right operand must be a callable requiring exactly one argument'
expect_failure examples/errors/nullary_composition.caret 'Line 3, column 12: Composition left operand must be a callable requiring at least one argument'
expect_failure examples/errors/call_depth.caret 'Maximum Caret evaluation depth exceeded'
printf 'All prototype tests passed.\n'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./run.sh examples/demo.caret > /tmp/caret-output.txt
cat > /tmp/caret-expected.txt <<'EXPECTED'
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
diff -u /tmp/caret-expected.txt /tmp/caret-output.txt

cat > /tmp/caret-language.caret <<'CARET'
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

./run.sh /tmp/caret-language.caret > /tmp/caret-language-output.txt
cat > /tmp/caret-language-expected.txt <<'EXPECTED'
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
diff -u /tmp/caret-language-expected.txt /tmp/caret-language-output.txt

cat > /tmp/caret-required-field.caret <<'CARET'
make =
  ^present = true
value = make
print value.absent
CARET
if ./run.sh /tmp/caret-required-field.caret > /tmp/caret-required-field.out 2>&1; then
  printf 'Required missing field unexpectedly succeeded.\n' >&2
  exit 1
fi
grep -F 'Scope has no exported binding: absent' /tmp/caret-required-field.out >/dev/null
printf 'All prototype tests passed.\n'

#!/usr/bin/env python3
"""
Checks that {}, () and [] balance in every Kotlin file.

The tree-sitter parser used by ktcheck.py is deliberately forgiving, so a stray
brace can still parse "well enough" to look clean. This is the blunt check that
catches it: strip strings, chars and comments, then count.
"""
import sys

PAIRS = {"}": "{", ")": "(", "]": "["}


def strip_code(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            if text.startswith('"""', i):
                i += 3
                while i < n and not text.startswith('"""', i):
                    if text[i] == "\\":
                        i += 1
                    i += 1
                i += 3
                continue
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            out.append("''")
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check(path):
    text = strip_code(open(path, encoding="utf-8").read())
    stack = []
    line = 1
    for ch in text:
        if ch == "\n":
            line += 1
            continue
        if ch in "{([":
            stack.append((ch, line))
        elif ch in PAIRS:
            if not stack:
                return "unmatched '%s' on line %d" % (ch, line)
            opening, opened_at = stack.pop()
            if opening != PAIRS[ch]:
                return "'%s' on line %d closes '%s' opened on line %d" % (
                    ch, line, opening, opened_at)
    if stack:
        ch, opened_at = stack[-1]
        return "'%s' opened on line %d is never closed" % (ch, opened_at)
    return None


def main():
    bad = 0
    for path in sys.argv[1:]:
        problem = check(path)
        if problem:
            print("%s: %s" % (path, problem))
            bad += 1
    print("\nchecked %d file(s), %d problem(s)" % (len(sys.argv) - 1, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

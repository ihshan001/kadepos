#!/usr/bin/env python3
"""
Flags imports whose short name never appears in the rest of the file.

Kotlin only warns about unused imports, so these would not break a build; the
point is to keep new files tidy.
"""
import re
import sys


def main():
    problems = 0
    for path in sys.argv[1:]:
        text = open(path, encoding="utf-8").read()
        lines = text.split("\n")
        body = "\n".join(line for line in lines if not line.startswith("import "))
        for line in lines:
            m = re.match(r"^import\s+([A-Za-z0-9_.]+)", line)
            if not m:
                continue
            name = m.group(1).rsplit(".", 1)[-1]
            if not re.search(r"\b%s\b" % re.escape(name), body):
                print("%s: unused import %s" % (path, m.group(1)))
                problems += 1
    print("\n%d unused import(s)" % problems)


if __name__ == "__main__":
    main()

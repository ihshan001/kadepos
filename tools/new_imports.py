#!/usr/bin/env python3
"""
Lists import lines that are new compared with a baseline (default: git HEAD).

The sandbox this project is edited in has no Android SDK, so a real Gradle
build cannot be run here. Every *new* library import is therefore a small risk:
it is the one thing a compiler is needed to check. Printing them makes that
risk visible so each one can be reviewed by hand.
"""
import re
import subprocess
import sys


def tracked_files():
    out = subprocess.check_output(["git", "ls-files", "*.kt"], text=True)
    return [f for f in out.split("\n") if f.strip()]


def head_text(path):
    try:
        return subprocess.check_output(
            ["git", "show", "HEAD:%s" % path], text=True, stderr=subprocess.DEVNULL
        )
    except subprocess.CalledProcessError:
        return ""


def imports(text):
    return set(re.findall(r"^import\s+([^\s]+)", text, re.MULTILINE))


def main():
    baseline = set()
    for path in tracked_files():
        baseline |= imports(head_text(path))

    targets = sys.argv[1:] or [
        f for f in subprocess.check_output(
            ["git", "status", "--porcelain", "*.kt"], text=True
        ).split("\n") if f.strip()
    ]
    targets = [t[3:].strip() if t[:2] in (" M", "M ", "A ", "??") else t for t in targets]
    targets = [t for t in targets if t.endswith(".kt")]

    risky = 0
    for path in targets:
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            continue
        for imp in sorted(imports(text)):
            if imp not in baseline:
                print("%s: NEW IMPORT %s" % (path, imp))
                risky += 1
    print("\n%d new import(s) to review across %d file(s)" % (risky, len(targets)))
    return 0


if __name__ == "__main__":
    sys.exit(main())

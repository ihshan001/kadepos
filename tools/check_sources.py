#!/usr/bin/env python3
"""
Static checks for the Kotlin sources, for when Gradle cannot be run.

This project normally builds with Gradle, which is the real check. These
scripts exist for the cases where a machine has the repository but no Android
SDK: they catch the mistakes that break a build — unbalanced braces, a symbol
used without importing it, a file that no longer parses — without being able to
compile anything.

    python3 tools/check_sources.py            # check the whole repository
    python3 tools/check_sources.py path/File.kt

Requirements: pip install tree-sitter tree-sitter-kotlin

Exit status is 1 when any check fails.

This is a heuristic, not a compiler: a name that exists both in the project and
in a library (SuggestionChip, say, or a private helper with a common name) will
be reported even though the code is fine. Read the list, do not obey it.
"""
import os
import re
import subprocess
import sys

try:
    import tree_sitter_kotlin as tsk
    from tree_sitter import Language, Parser
except ImportError:
    sys.exit("pip install tree-sitter tree-sitter-kotlin")

PARSER = Parser(Language(tsk.language()))

DECL = re.compile(
    r"\b(?:class|object|interface|fun|val|var|typealias)\s+([A-Z][A-Za-z0-9_]*)\b(?!\s*\.)")
TOP_LEVEL = re.compile(
    r"^(?:@\w+\s+)*(?:public |internal |private |suspend )*(?:inline )?"
    r"(fun|val|var|object|class|interface)\s+([a-zA-Z_][A-Za-z0-9_]*)\b(?!\s*\.)", re.M)

# Types that come from Kotlin itself, not from this repository.
BUILTINS = {
    "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Short", "Byte",
    "Any", "Unit", "Nothing", "List", "Map", "Set", "Array", "Pair", "Triple",
    "Locale", "Date", "SimpleDateFormat", "DecimalFormat", "JSONObject", "JSONArray",
    "File", "Regex", "StringBuilder", "Result", "IllegalStateException",
    "IllegalArgumentException", "NumberFormatException", "Exception", "Throwable",
}


# --------------------------------------------------------------------------
# 1. Parsing (catches syntax errors)
# --------------------------------------------------------------------------

def parse_errors(text):
    tree = PARSER.parse(text.encode("utf-8"))
    bad = []

    def walk(node):
        if node.type == "ERROR" or node.is_missing:
            bad.append(node.start_point[0] + 1)
        for child in node.children:
            walk(child)

    walk(tree.root_node)
    return bad


# --------------------------------------------------------------------------
# 2. Balanced braces
# --------------------------------------------------------------------------

PAIRS = {"}": "{", ")": "(", "]": "["}


def strip_code(text):
    """Removes string literals, characters and comments."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            if text.startswith('"""', i):
                i += 3
                while i < n and not text.startswith('"""', i):
                    i += 2 if text[i] == "\\" else 1
                i += 3
                continue
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
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


def brace_problems(text):
    stack = []
    line = 1
    for ch in text:
        if ch == "\n":
            line += 1
        elif ch in "{([":
            stack.append((ch, line))
        elif ch in PAIRS:
            if not stack:
                return ["unmatched '%s' on line %d" % (ch, line)]
            opening, opened_at = stack.pop()
            if opening != PAIRS[ch]:
                return ["'%s' on line %d closes '%s' from line %d" % (ch, line, opening, opened_at)]
    if stack:
        ch, opened_at = stack[-1]
        return ["'%s' opened on line %d is never closed" % (ch, opened_at)]
    return []


# --------------------------------------------------------------------------
# 3. Missing imports
# --------------------------------------------------------------------------

def member_free(text):
    """strip_code plus removal of `foo.Bar` member accesses and kdoc lines."""
    out = []
    for line in strip_code(text).split("\n"):
        line = re.sub(r"^\s*(\*|//).*$", "", line)
        out.append(re.sub(r"\.([A-Za-z_][A-Za-z0-9_]*)", "", line))
    return "\n".join(out)


def index_declarations(paths):
    capitalised, top_level, packages = {}, {}, {}
    for path in paths:
        text = open(path, encoding="utf-8").read()
        match = re.search(r"^package\s+([\w.]+)", text, re.M)
        packages[path] = match.group(1) if match else ""
        code = strip_code(text)
        for name in DECL.findall(code):
            capitalised.setdefault(name, set()).add(packages[path])
        for _, name in TOP_LEVEL.findall(code):
            top_level.setdefault(name, set()).add(packages[path])
    return capitalised, top_level, packages


def missing_imports(paths, capitalised, top_level, packages):
    problems = []
    for path in paths:
        text = open(path, encoding="utf-8").read()
        code = strip_code(text)
        imported = {m.rsplit(".", 1)[-1] for m in re.findall(r"^import\s+([\w.]+)", code, re.M)}
        wildcards = re.findall(r"^import\s+([\w.]+)\.\*", code, re.M)
        own = packages[path]
        local = set(DECL.findall(code))
        local |= {name for _, name in TOP_LEVEL.findall(code)}
        local |= set(re.findall(r"\b(?:val|var|fun)\s+([A-Za-z_][A-Za-z0-9_]*)", code))
        local |= set(re.findall(r"([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[A-Za-z]|->|,|\))", code))

        body = member_free(text)
        used = set(re.findall(r"\b([A-Za-z_][A-Za-z0-9_]*)\b", body))
        for name in sorted(used):
            if name in local or name in imported or name in BUILTINS or len(name) < 4:
                continue
            sources = (capitalised.get(name, set()) | top_level.get(name, set())) - {own}
            if not sources:
                continue
            if any(p == w or p.startswith(w) for p in sources for w in wildcards):
                continue
            problems.append("%s: '%s' is declared in %s but never imported here"
                            % (path, name, sorted(sources)))
    return problems


# --------------------------------------------------------------------------

def main():
    if len(sys.argv) > 1:
        paths = [a for a in sys.argv[1:] if a.endswith(".kt")]
    else:
        paths = [f for f in subprocess.check_output(
            ["git", "ls-files", "*.kt"], text=True).split("\n") if f.strip()]
    paths = [p for p in paths if os.path.exists(p)]

    failures = []
    for path in paths:
        text = open(path, encoding="utf-8").read()
        for line in parse_errors(text)[:3]:
            failures.append("%s:%d: does not parse" % (path, line))
        for problem in brace_problems(strip_code(text)):
            failures.append("%s: %s" % (path, problem))

    capitalised, top_level, packages = index_declarations(paths)
    failures += missing_imports(paths, capitalised, top_level, packages)

    for failure in failures:
        print(failure)
    print("\nchecked %d file(s), %d problem(s)" % (len(paths), len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Flags capitalised identifiers that are neither imported nor declared anywhere.

There is no Android SDK in this sandbox, so the real compiler cannot check the
code. Every capitalised name in Kotlin is a type, a composable or an enum
entry — all of which have to come from somewhere — so a name that appears in no
import and no declaration in the repository is either a typo or a missing
import. Both break a build, so both are worth a look.

Usage: python3 tools/symbol_check.py [file ...]
"""
import os
import re
import subprocess
import sys
import importlib.util

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location(
    "brace_check", os.path.join(os.path.dirname(os.path.abspath(__file__)), "brace_check.py"))
brace_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(brace_check)

BUILTINS = {
    "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Short", "Byte",
    "Any", "Unit", "Nothing", "List", "MutableList", "Set", "MutableSet", "Map",
    "MutableMap", "Iterable", "Sequence", "Array", "IntArray", "DoubleArray",
    "BooleanArray", "LongArray", "FloatArray", "CharArray", "Pair", "Triple",
    "StringBuilder", "Regex", "RegexOption", "Throwable", "Exception", "Error",
    "RuntimeException", "IllegalArgumentException", "IllegalStateException",
    "IndexOutOfBoundsException", "NumberFormatException", "Lazy", "Comparable",
    "Enum", "Annotation", "Deprecated", "Suppress", "OptIn", "JvmName", "JvmStatic",
    "JvmOverloads", "JvmField", "Throws", "Volatile", "Synchronized", "Transient",
    "Locale", "Date", "Calendar", "SimpleDateFormat", "DecimalFormat", "TimeZone",
    "File", "InputStream", "OutputStream", "JSONObject", "JSONArray", "Byte",
    "Thread", "Runnable", "System", "Math", "Objects", "Random", "SecureRandom",
    "Result", "Success", "Failure", "Unit", "Byte", "UByte", "UInt", "ULong",
    "StringBuilder", "ArrayList", "HashMap", "LinkedHashMap", "LinkedHashSet",
    "HashSet", "Collection", "MutableCollection", "MutableIterator", "Iterator",
    "Number", "Void", "Class", "Object", "Package", "Process", "StringBuilder",
    "Composable", "Preview", "Test", "Before", "After", "Ignore", "RunWith",
    "Config", "Parameterized", "Float", "Int", "DeprecationLevel", "ReplaceWith",
    "DelicateCoroutinesApi", "ExperimentalCoroutinesApi", "FlowPreview",
}


def repo_declarations():
    names = set()
    out = subprocess.check_output(["git", "ls-files", "*.kt"], text=True)
    for path in out.split("\n"):
        if not path.strip() or not os.path.exists(path):
            continue
        text = brace_check.strip_code(open(path, encoding="utf-8").read())
        names |= set(re.findall(
            r"\b(?:class|object|interface|fun|val|var|typealias|enum class|data class|"
            r"private fun|internal fun|suspend fun)\s+([A-Z][A-Za-z0-9_]*)", text))
        names |= set(re.findall(r"^typealias\s+([A-Z][A-Za-z0-9_]*)", text, re.M))
    return names


def imported_names(paths):
    names = set()
    for path in paths:
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        for line in text.split("\n"):
            m = re.match(r"^import\s+([A-Za-z0-9_.]+)", line)
            if m:
                names.add(m.group(1).rsplit(".", 1)[-1])
    return names


def main():
    targets = sys.argv[1:]
    if not targets:
        out = subprocess.check_output(["git", "ls-files", "*.kt"], text=True)
        targets = [f for f in out.split("\n") if f.strip()]

    allowed = repo_declarations() | imported_names(targets) | BUILTINS

    problems = 0
    for path in targets:
        text = brace_check.strip_code(open(path, encoding="utf-8").read())
        local = set(re.findall(
            r"\b(?:class|object|interface|fun|val|var|typealias|enum class|data class)\s+"
            r"([A-Z][A-Za-z0-9_]*)", text))
        for lineno, line in enumerate(text.split("\n"), start=1):
            stripped = re.sub(r"^\s*\*", "", line)  # ignore kdoc continuation lines
            # Drop the member half of qualified names: Foo.Bar -> Foo
            cleaned = re.sub(r"\.\s*([A-Za-z_][A-Za-z0-9_]*)", "", stripped)
            cleaned = re.sub(r"([A-Za-z_][A-Za-z0-9_]*)\s*\.", r"\1", cleaned)
            for token in re.findall(r"\b([A-Z][A-Za-z0-9_]*)\b", cleaned):
                if token in allowed or token in local:
                    continue
                print("%s:%d: unknown symbol %s" % (path, lineno, token))
                problems += 1
    print("\n%d unknown symbol(s) in %d file(s)" % (problems, len(targets)))
    return 0


if __name__ == "__main__":
    sys.exit(main())

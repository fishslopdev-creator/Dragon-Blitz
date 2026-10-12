#!/usr/bin/env python3
"""Regenerates engine-core/src/main/resources/playtime/builtin/index.json and the sample index.

Run after adding, renaming or removing built-in or sample content files. The engine reads
these indexes because a jar cannot list its own resource folders reliably.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "engine-core", "src", "main", "resources", "playtime")


def index(folder):
    out = []
    for dirpath, _, files in os.walk(folder):
        for f in files:
            if f.endswith(".json") and f != "index.json":
                out.append(os.path.relpath(os.path.join(dirpath, f), folder).replace(os.sep, "/"))
    return sorted(out)


def write(path, entries):
    with open(path, "w") as fh:
        json.dump(entries, fh, indent=2)
        fh.write("\n")
    print(f"{path}: {len(entries)} entries")


write(os.path.join(RES, "builtin", "index.json"), index(os.path.join(RES, "builtin")))
write(os.path.join(RES, "sample", "index.json"), index(os.path.join(RES, "sample", "content")))

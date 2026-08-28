#!/usr/bin/env python3
"""One-time: assign a permanent, stable `id` to every subject/node in
syllabus_cgl.json's `tier1` tree, derived from a slugified name path.

Positional keys (t1_0_3_2) break when nodes are reordered. IDs derived here
are written into the JSON once and become permanent — do not re-run this
against a syllabus version that already has IDs assigned, and do not derive
IDs from array position at runtime anywhere in the app.

Usage: python3 tools/assign_syllabus_ids.py
Edits syllabus_cgl.json in place.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SYLLABUS_PATH = ROOT / "syllabus_cgl.json"


def slugify(name: str) -> str:
    slug = name.lower()
    slug = slug.replace("&", "and")
    slug = re.sub(r"[^a-z0-9]+", "_", slug)
    slug = slug.strip("_")
    return slug or "node"


def unique(base: str, used: set[str]) -> str:
    if base not in used:
        used.add(base)
        return base
    i = 2
    while f"{base}_{i}" in used:
        i += 1
    candidate = f"{base}_{i}"
    used.add(candidate)
    return candidate


def assign_node_ids(node: dict, parent_id: str, used: set[str]) -> None:
    node_id = unique(f"{parent_id}.{slugify(node['n'])}", used)
    node["id"] = node_id
    for child in node.get("c", []):
        assign_node_ids(child, node_id, used)


def assign_section_ids(sections: list[dict]) -> None:
    used: set[str] = set()
    for section in sections:
        section_id = unique(slugify(section["n"]), used)
        section["id"] = section_id
        for topic in section.get("t", []):
            assign_node_ids(topic, section_id, used)


def main() -> None:
    data = json.loads(SYLLABUS_PATH.read_text())
    if any("id" in s for s in data["tier1"]):
        print("tier1 sections already have ids — refusing to reassign.", file=sys.stderr)
        sys.exit(1)
    assign_section_ids(data["tier1"])
    SYLLABUS_PATH.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"Assigned ids to {len(data['tier1'])} tier1 sections.")


if __name__ == "__main__":
    main()

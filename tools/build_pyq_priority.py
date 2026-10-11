#!/usr/bin/env python3
"""Builds android/app/src/main/assets/neet_pyq_priority.json from the PYQ research draft.

Reads docs/research/pyq_weightage_draft.json (every number there is a count a
source states for one year's paper) and writes one priority record per chapter.
No number is hand-edited: rerun this script after the draft changes.

Usage: python3 tools/build_pyq_priority.py [draft.json] [output.json]

Method (also written into the output's "method" field):
  1. Only paper "main" and years 2021-2025 are used. 2026 papers are excluded
     (one cancelled, one re-exam, one unlabelled) and 2019-2020 have no
     chapter-level counts for most subjects.
  2. A "source" is one published page for one year (source URL + year). Rows a
     page lists for the same chapter (componentOfMerged rows, e.g. PW's
     "Rotational Motion" + "Center of Mass") are summed first.
  3. Within a year, a source whose whole chapter->questions table is identical
     to another source's is a copy; only one copy is kept.
  4. Per chapter and year, the value is the median over the remaining sources.
  5. Chapter score = mean of its yearly medians. At least 2 years are
     required, otherwise the band is NO_DATA.
  6. Within each subject, chapters with data are ranked by score and split into
     terciles: top third HIGH, middle third MEDIUM, bottom third LOW. Tied
     chapters always share the band of the best rank in their tie group.
"""
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_IN = ROOT / "docs" / "research" / "pyq_weightage_draft.json"
DEFAULT_OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "neet_pyq_priority.json"

YEARS = range(2021, 2026)
MIN_YEARS = 2
PAPER = "main"
# Which copy survives de-duplication: earlier in this list wins.
SOURCE_ORDER = ["PW", "Careers360", "Collegedunia", "Vedantu", "Byjus", "Unacademy", "Testbook", "NEETprep"]

METHOD = (
    "Paper 'main' only, years 2021-2025. A source is one published page for one year; rows a page lists "
    "for the same chapter (componentOfMerged) are summed first. Within a year, a source whose whole "
    "chapter table is identical to another source's is a copy and only one copy is kept. Per chapter and "
    "year the value is the median across the remaining sources. Chapter score = mean of its yearly "
    "medians; fewer than 2 years gives NO_DATA. Per subject, chapters with data are ranked by score and "
    "split into terciles (top third HIGH, middle MEDIUM, bottom LOW; ties share the best rank's band). "
    "Counts are as stated by each source and are unverified; nothing is projected or expected."
)


def source_rank(name: str) -> int:
    return SOURCE_ORDER.index(name) if name in SOURCE_ORDER else len(SOURCE_ORDER)


def subject_of(chapter_id: str) -> str:
    # ids look like neet.<subject>.<class>.<name>
    return chapter_id.split(".")[1]


def band_for(rank: int, count: int) -> str:
    if rank * 3 < count:
        return "HIGH"
    if rank * 3 < 2 * count:
        return "MEDIUM"
    return "LOW"


def build(draft: dict) -> dict:
    # (year, url) -> {chapter: summed questions}, plus the url's source name
    pages = defaultdict(lambda: defaultdict(int))
    page_name = {}
    for chapter_id, rows in draft["chapters"].items():
        for row in rows:
            if row["paper"] != PAPER or row["year"] not in YEARS:
                continue
            key = (row["year"], row["source"])
            pages[key][chapter_id] += row["questions"]
            page_name[key] = row["sourceName"]

    # Drop copies: identical whole tables within a year.
    kept = {}
    dropped = 0
    by_signature = defaultdict(list)
    for key, table in pages.items():
        signature = (key[0], tuple(sorted(table.items())))
        by_signature[signature].append(key)
    for keys in by_signature.values():
        keys.sort(key=lambda k: (source_rank(page_name[k]), k[1]))
        kept[keys[0]] = pages[keys[0]]
        dropped += len(keys) - 1

    # Per chapter and year: median over remaining sources.
    yearly = defaultdict(dict)  # chapter -> year -> median
    used_sources = defaultdict(set)  # chapter -> source names that contributed
    for chapter_id in draft["chapters"]:
        for year in YEARS:
            values = []
            names = set()
            for (page_year, url), table in kept.items():
                if page_year == year and chapter_id in table:
                    values.append(table[chapter_id])
                    names.add(page_name[(page_year, url)])
            if values:
                yearly[chapter_id][year] = statistics.median(values)
                used_sources[chapter_id] |= names

    scores = {}
    for chapter_id in draft["chapters"]:
        per_year = yearly.get(chapter_id, {})
        if len(per_year) >= MIN_YEARS:
            scores[chapter_id] = sum(per_year.values()) / len(per_year)

    bands = {}
    by_subject = defaultdict(list)
    for chapter_id, score in scores.items():
        by_subject[subject_of(chapter_id)].append((chapter_id, score))
    for chapters in by_subject.values():
        chapters.sort(key=lambda item: (-item[1], item[0]))
        first_rank = {}
        for index, (_, score) in enumerate(chapters):
            first_rank.setdefault(score, index)
        for chapter_id, score in chapters:
            bands[chapter_id] = band_for(first_rank[score], len(chapters))

    out_chapters = {}
    for chapter_id in sorted(draft["chapters"]):
        per_year = yearly.get(chapter_id, {})
        if chapter_id in scores:
            out_chapters[chapter_id] = {
                "band": bands[chapter_id],
                "meanQuestionsPerYear": round(scores[chapter_id], 1),
                "yearsUsed": len(per_year),
                "sourceNames": sorted(used_sources[chapter_id]),
            }
        else:
            out_chapters[chapter_id] = {
                "band": "NO_DATA",
                "meanQuestionsPerYear": None,
                "yearsUsed": len(per_year),
                "sourceNames": sorted(used_sources[chapter_id]),
            }

    return {
        "generatedFrom": "docs/research/pyq_weightage_draft.json",
        "dataStatus": draft.get("status", "unknown"),
        "years": [min(YEARS), max(YEARS)],
        "method": METHOD,
        "copiesDropped": dropped,
        "chapters": out_chapters,
    }


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUT
    result = build(json.loads(src.read_text()))
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(result, indent=1, ensure_ascii=False) + "\n")

    counts = defaultdict(lambda: defaultdict(int))
    for chapter_id, info in result["chapters"].items():
        counts[subject_of(chapter_id)][info["band"]] += 1
    print(f"wrote {dst} ({len(result['chapters'])} chapters, {result['copiesDropped']} copy tables dropped)")
    for subject in sorted(counts):
        print(" ", subject, dict(sorted(counts[subject].items())))


if __name__ == "__main__":
    main()

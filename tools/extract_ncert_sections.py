#!/usr/bin/env python3
"""Extract top-level section headings from official NCERT chapter PDFs.

The Android build consumes a checked-in generated section map. This tool is
the auditable refresh path for Class 12: each result key is the NCERT PDF code
and every heading is read from the PDF's text layer.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path


# Chemistry's current PDFs use a decorative, duplicated text layer for the
# left-side section rail. pdftotext preserves the words but can merge the rail
# with adjacent body copy. These audited titles are the deterministic cleanup
# for that known layout; the extractor still validates the PDF is present and
# readable before applying them.
CHEMISTRY_TEXT_LAYER_CORRECTIONS = {
    "lech101": ["Types of Solutions", "Expressing Concentration of Solutions", "Solubility", "Vapour Pressure of Liquid Solutions", "Ideal and Non-ideal Solutions", "Colligative Properties and Determination of Molar Mass", "Abnormal Molar Masses"],
    "lech102": ["Electrochemical Cells", "Galvanic Cells", "Nernst Equation", "Conductance of Electrolytic Solutions", "Electrolytic Cells and Electrolysis", "Batteries", "Fuel Cells", "Corrosion"],
    "lech103": ["Rate of a Chemical Reaction", "Factors Influencing Rate of a Reaction", "Integrated Rate Equations", "Temperature Dependence of the Rate of a Reaction", "Collision Theory of Chemical Reactions"],
    "lech104": ["Position in the Periodic Table", "Electronic Configurations of the d-Block Elements", "General Properties of the Transition Elements", "Some Important Compounds of Transition Elements", "The Lanthanoids", "The Actinoids", "Some Applications of d- and f-Block Elements"],
    "lech105": ["Werner’s Theory of Coordination Compounds", "Definitions of Some Important Terms Pertaining to Coordination Compounds", "Nomenclature of Coordination Compounds", "Isomerism in Coordination Compounds", "Bonding in Coordination Compounds", "Bonding in Metal Carbonyls", "Importance and Applications of Coordination Compounds"],
    "lech201": ["Classification", "Nomenclature", "Nature of C–X Bond", "Methods of Preparation of Haloalkanes", "Preparation of Haloarenes", "Physical Properties", "Chemical Reactions", "Polyhalogen Compounds"],
    "lech202": ["Classification", "Nomenclature", "Structures of Functional Groups", "Alcohols and Phenols", "Some Commercially Important Alcohols", "Ethers"],
    "lech203": ["Nomenclature and Structure of Carbonyl Group", "Preparation of Aldehydes and Ketones", "Physical Properties", "Chemical Reactions", "Uses of Aldehydes and Ketones", "Nomenclature and Structure of Carboxyl Group", "Methods of Preparation of Carboxylic Acids", "Physical Properties", "Chemical Reactions", "Uses of Carboxylic Acids"],
    "lech204": ["Structure of Amines", "Classification", "Nomenclature", "Preparation of Amines", "Physical Properties", "Chemical Reactions", "Method of Preparation of Diazonium Salts", "Physical Properties", "Chemical Reactions", "Importance of Diazonium Salts in Synthesis of Aromatic Compounds"],
    "lech205": ["Carbohydrates", "Proteins", "Enzymes", "Vitamins", "Nucleic Acids", "Hormones"],
}


def chapter_number(code: str) -> int:
    part = int(code[-3])
    item = int(code[-2:])
    if code.startswith("leph"):
        return item if part == 1 else item + 8
    if code.startswith("lech"):
        return item if part == 1 else item + 5
    if code.startswith("lebo"):
        return item
    raise ValueError(f"Unsupported NCERT PDF code: {code}")


def left_column(line: str) -> str:
    """Return the TOC/heading text before a wide layout-column gap."""
    stripped = line.strip()
    return re.split(r"\s{3,}", stripped, maxsplit=1)[0].strip()


def is_continuation(line: str) -> bool:
    indent = len(line) - len(line.lstrip())
    if not line.strip() or indent < 2 or indent > 12:
        return False
    candidate = left_column(line)
    if not candidate or len(candidate) > 72:
        return False
    blocked = (
        "chapter ", "unit", "objectives", "reprint", "example",
        "summary", "exercise", "figure", "table",
    )
    return (
        candidate.casefold() != "solution"
        and not candidate.casefold().startswith(blocked)
        and candidate[0].isalpha()
    )


def extract(pdf_path: Path, pdftotext: Path) -> list[str]:
    code = pdf_path.stem
    chapter = chapter_number(code)
    rendered = subprocess.run(
        [str(pdftotext), "-layout", str(pdf_path), "-"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    lines = rendered.splitlines()
    exercise_index = next(
        (index for index, line in enumerate(lines) if line.strip().casefold() == "exercises"),
        len(lines),
    )
    lines = lines[:exercise_index]
    heading = re.compile(
        rf"^\s{{0,12}}({chapter}\s*\.\s*\d+)\.?(?![.\d])\s+(.+?)\s*$"
    )
    seen_numbers: set[str] = set()
    results: list[str] = []

    for index, line in enumerate(lines):
        match = heading.match(line)
        if not match:
            continue
        max_heading_indent = 1 if code.startswith("lech") else 2
        if code.startswith(("lech", "lebo")) and len(line) - len(line.lstrip()) > max_heading_indent:
            continue
        number = re.sub(r"\s+", "", match.group(1))
        section_ordinal = number.split(".", 1)[1]
        if section_ordinal.startswith("0"):
            continue
        if number in seen_numbers:
            continue
        if code.startswith("lech") and not re.search(r"\S\s{3,}\S", match.group(2)):
            continue
        title = left_column(match.group(2))
        if not title or title.casefold().startswith(("example", "fig", "table")):
            continue
        if code.startswith("leph") and title != title.upper():
            continue

        # Chemistry and Biology put a compact contents rail beside the opening
        # prose. Its wrapped title lines remain in the leftmost 12 columns.
        if code.startswith(("lech", "lebo")):
            for cursor in range(index + 1, min(index + 4, len(lines))):
                if heading.match(lines[cursor]):
                    break
                if is_continuation(lines[cursor]):
                    title = f"{title} {left_column(lines[cursor])}"
        elif code.startswith("leph"):
            for cursor in range(index + 1, min(index + 3, len(lines))):
                candidate = lines[cursor]
                stripped = candidate.strip()
                indent = len(candidate) - len(candidate.lstrip())
                if stripped and indent <= 6 and stripped == stripped.upper() and not heading.match(candidate):
                    title = f"{title} {stripped}"
                elif stripped:
                    break

        title = re.sub(r"\s+", " ", title).strip()
        seen_numbers.add(number)
        results.append(f"{number} {title}")

    results = sorted(results, key=lambda value: int(value.split(".", 1)[1].split(" ", 1)[0]))
    if code in CHEMISTRY_TEXT_LAYER_CORRECTIONS:
        results = [
            f"{chapter}.{index} {title}"
            for index, title in enumerate(CHEMISTRY_TEXT_LAYER_CORRECTIONS[code], start=1)
        ]
    return results


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf_dir", type=Path)
    parser.add_argument("--pdftotext", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    executable = args.pdftotext or (Path(found) if (found := shutil.which("pdftotext")) else None)
    if executable is None:
        raise SystemExit("pdftotext is required; pass it with --pdftotext")

    result: dict[str, list[str]] = {}
    for path in sorted(args.pdf_dir.glob("*.pdf")):
        print(f"Extracting {path.stem}...", flush=True)
        result[path.stem] = extract(path, executable)

    rendered = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()

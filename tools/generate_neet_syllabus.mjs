#!/usr/bin/env node

/**
 * Generates the versioned native NEET syllabus asset from the rationalised
 * NCERT Class 11 and 12 chapter lists. Chapter IDs are permanent product IDs;
 * do not derive them from array positions at runtime.
 *
 * Sources checked for this version:
 * - NCERT Class 11/12 Physics, Chemistry and Biology books (2025-26/2026-27)
 * - NMC syllabus for NEET (UG) 2026
 */

import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const sectionCatalog = JSON.parse(
  await readFile(join(root, "ncert_sections_neet.json"), "utf8"),
).sections;

const subjects = [
  {
    id: "physics",
    name: "Physics",
    questions: 45,
    hoursPerChapter: 8,
    chapters: {
      11: [
        "Units and Measurements",
        "Motion in a Straight Line",
        "Motion in a Plane",
        "Laws of Motion",
        "Work, Energy and Power",
        "System of Particles and Rotational Motion",
        "Gravitation",
        "Mechanical Properties of Solids",
        "Mechanical Properties of Fluids",
        "Thermal Properties of Matter",
        "Thermodynamics",
        "Kinetic Theory",
        "Oscillations",
        "Waves",
      ],
      12: [
        "Electric Charges and Fields",
        "Electrostatic Potential and Capacitance",
        "Current Electricity",
        "Moving Charges and Magnetism",
        "Magnetism and Matter",
        "Electromagnetic Induction",
        "Alternating Current",
        "Electromagnetic Waves",
        "Ray Optics and Optical Instruments",
        "Wave Optics",
        "Dual Nature of Radiation and Matter",
        "Atoms",
        "Nuclei",
        "Semiconductor Electronics",
      ],
    },
  },
  {
    id: "chemistry",
    name: "Chemistry",
    questions: 45,
    hoursPerChapter: 12,
    chapters: {
      11: [
        "Some Basic Concepts of Chemistry",
        "Structure of Atom",
        "Classification of Elements and Periodicity in Properties",
        "Chemical Bonding and Molecular Structure",
        "Thermodynamics",
        "Equilibrium",
        "Redox Reactions",
        "Organic Chemistry - Some Basic Principles and Techniques",
        "Hydrocarbons",
      ],
      12: [
        "Solutions",
        "Electrochemistry",
        "Chemical Kinetics",
        "The d- and f-Block Elements",
        "Coordination Compounds",
        "Haloalkanes and Haloarenes",
        "Alcohols, Phenols and Ethers",
        "Aldehydes, Ketones and Carboxylic Acids",
        "Amines",
        "Biomolecules",
      ],
    },
  },
  {
    id: "botany",
    name: "Botany",
    questions: 45,
    hoursPerChapter: 11.25,
    chapters: {
      11: [
        "The Living World",
        "Biological Classification",
        "Plant Kingdom",
        "Morphology of Flowering Plants",
        "Anatomy of Flowering Plants",
        "Cell: The Unit of Life",
        "Biomolecules",
        "Cell Cycle and Cell Division",
        "Photosynthesis in Higher Plants",
        "Respiration in Plants",
        "Plant Growth and Development",
      ],
      12: [
        "Sexual Reproduction in Flowering Plants",
        "Principles of Inheritance and Variation",
        "Molecular Basis of Inheritance",
        "Evolution",
        "Biotechnology: Principles and Processes",
        "Biotechnology and its Applications",
        "Organisms and Populations",
        "Ecosystem",
        "Biodiversity and Conservation",
      ],
    },
  },
  {
    id: "zoology",
    name: "Zoology",
    questions: 45,
    hoursPerChapter: 18.75,
    chapters: {
      11: [
        "Animal Kingdom",
        "Structural Organisation in Animals",
        "Breathing and Exchange of Gases",
        "Body Fluids and Circulation",
        "Excretory Products and their Elimination",
        "Locomotion and Movement",
        "Neural Control and Coordination",
        "Chemical Coordination and Integration",
      ],
      12: [
        "Human Reproduction",
        "Reproductive Health",
        "Human Health and Disease",
        "Microbes in Human Welfare",
      ],
    },
  },
];

function slug(value) {
  return value
    .toLowerCase()
    .replaceAll("&", " and ")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

const pack = {
  schemaVersion: 2,
  examId: "neet",
  displayName: "NEET UG",
  syllabusVersion: "ncert-rationalised-2026-2",
  source: {
    scope: "NMC NEET (UG) 2026 syllabus",
    chapters: "NCERT rationalised Class 11 and 12 textbook contents",
    note: "Botany and Zoology are a planning split of the NCERT Biology books.",
  },
  tier1: subjects.map((subject) => ({
    id: subject.id,
    n: subject.name,
    q: subject.questions,
    t: Object.entries(subject.chapters).flatMap(([classNumber, chapters]) =>
      chapters.map((name) => {
        const chapterId = `neet.${subject.id}.${classNumber}.${slug(name)}`;
        const headings = sectionCatalog[`${subject.id}::${name}`]
          ?? sectionCatalog[`biology::${name}`];
        if (!headings?.length) throw new Error(`Missing NCERT sections for ${subject.id}::${name}`);
        const totalMinutes = Math.round(subject.hoursPerChapter * 60);
        const baseMinutes = Math.floor(totalMinutes / headings.length);
        const remainder = totalMinutes % headings.length;
        return {
          id: chapterId,
          n: name,
          class: Number(classNumber),
          c: headings.map((heading, index) => ({
            id: `${chapterId}.section_${String(index + 1).padStart(2, "0")}`,
            n: heading,
            m: baseMinutes + (index < remainder ? 1 : 0),
          })),
        };
      }),
    ),
  })),
};

const json = `${JSON.stringify(pack, null, 2)}\n`;
const outputPaths = [
  join(root, "syllabus_neet.json"),
  join(root, "android/app/src/main/assets/syllabus_neet.json"),
];
const expectedCounts = { physics: 28, chemistry: 19, botany: 20, zoology: 12 };
const actualCounts = Object.fromEntries(pack.tier1.map((subject) => [subject.id, subject.t.length]));
if (JSON.stringify(actualCounts) !== JSON.stringify(expectedCounts)) {
  throw new Error(`Unexpected chapter counts: ${JSON.stringify(actualCounts)}`);
}
const ids = pack.tier1.flatMap((subject) => subject.t.map((chapter) => chapter.id));
if (ids.length !== 79 || new Set(ids).size !== ids.length) {
  throw new Error(`Expected 79 unique chapter IDs; found ${ids.length} total and ${new Set(ids).size} unique.`);
}
const sectionIds = pack.tier1.flatMap((subject) => subject.t.flatMap((chapter) => chapter.c.map((section) => section.id)));
if (sectionIds.length !== 512 || new Set(sectionIds).size !== sectionIds.length) {
  throw new Error(`Expected 512 unique NCERT section IDs; found ${sectionIds.length} total and ${new Set(sectionIds).size} unique.`);
}
const chapterTotals = pack.tier1.flatMap((subject) => subject.t.map((chapter) => chapter.c.reduce((sum, section) => sum + section.m, 0)));
if (chapterTotals.some((minutes, index) => minutes !== Math.round(subjects.flatMap((subject) => Object.values(subject.chapters).flat().map(() => subject.hoursPerChapter))[index] * 60))) {
  throw new Error("Distributed section minutes no longer preserve chapter totals.");
}

if (process.argv.includes("--check")) {
  const current = await Promise.all(outputPaths.map((path) => readFile(path, "utf8")));
  if (current.some((contents) => contents !== json)) {
    throw new Error("Generated NEET syllabus files are stale. Run this script without --check.");
  }
  console.log("Verified 79 NEET chapters, 512 NCERT sections, stable IDs, minutes, and matching generated files.");
} else {
  await Promise.all(outputPaths.map((path) => writeFile(path, json)));
  console.log("Generated 79 NEET chapters and 512 NCERT sections across 4 subjects.");
}

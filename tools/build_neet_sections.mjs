#!/usr/bin/env node

/** One-time/maintainer builder for the canonical NCERT section map. */

import { readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const class12Path = process.argv[2];
if (!class12Path) throw new Error("Pass the extracted Class 12 JSON path.");

const class11Source = await readFile(
  join(root, "prototype/onboarding-ng/src/app/onboarding/class11-subtopics.ts"),
  "utf8",
);
const objectLiteral = class11Source.slice(class11Source.indexOf("{"), class11Source.lastIndexOf("}") + 1);
const class11 = Function(`"use strict"; return (${objectLiteral});`)();
const class12ByPdf = JSON.parse(await readFile(resolve(class12Path), "utf8"));

const physics = [
  "Electric Charges and Fields", "Electrostatic Potential and Capacitance", "Current Electricity",
  "Moving Charges and Magnetism", "Magnetism and Matter", "Electromagnetic Induction",
  "Alternating Current", "Electromagnetic Waves", "Ray Optics and Optical Instruments",
  "Wave Optics", "Dual Nature of Radiation and Matter", "Atoms", "Nuclei", "Semiconductor Electronics",
];
const chemistry = [
  "Solutions", "Electrochemistry", "Chemical Kinetics", "The d- and f-Block Elements",
  "Coordination Compounds", "Haloalkanes and Haloarenes", "Alcohols, Phenols and Ethers",
  "Aldehydes, Ketones and Carboxylic Acids", "Amines", "Biomolecules",
];
const biology = [
  "Sexual Reproduction in Flowering Plants", "Human Reproduction", "Reproductive Health",
  "Principles of Inheritance and Variation", "Molecular Basis of Inheritance", "Evolution",
  "Human Health and Disease", "Microbes in Human Welfare", "Biotechnology: Principles and Processes",
  "Biotechnology and its Applications", "Organisms and Populations", "Ecosystem",
  "Biodiversity and Conservation",
];

const pdfCodes = [
  ...Array.from({ length: 8 }, (_, index) => `leph1${String(index + 1).padStart(2, "0")}`),
  ...Array.from({ length: 6 }, (_, index) => `leph2${String(index + 1).padStart(2, "0")}`),
  ...Array.from({ length: 5 }, (_, index) => `lech1${String(index + 1).padStart(2, "0")}`),
  ...Array.from({ length: 5 }, (_, index) => `lech2${String(index + 1).padStart(2, "0")}`),
  ...Array.from({ length: 13 }, (_, index) => `lebo1${String(index + 1).padStart(2, "0")}`),
];
const class12Names = [...physics, ...chemistry, ...biology];
const class12Subjects = [
  ...Array(physics.length).fill("physics"),
  ...Array(chemistry.length).fill("chemistry"),
  ...Array(biology.length).fill("biology"),
];

const sections = { ...class11 };
pdfCodes.forEach((code, index) => {
  const headings = class12ByPdf[code];
  if (!headings?.length) throw new Error(`No headings extracted for ${code}.`);
  sections[`${class12Subjects[index]}::${class12Names[index]}`] = headings;
});

if (Object.keys(sections).length !== 79) {
  throw new Error(`Expected sections for 79 chapters; found ${Object.keys(sections).length}.`);
}
if (Object.values(sections).some((headings) => headings.length === 0)) {
  throw new Error("Every chapter must contain at least one NCERT section.");
}

const output = {
  schemaVersion: 1,
  source: "NCERT Class 11 and 12 chapter PDFs, Reprint 2026-27",
  sections,
};
await writeFile(join(root, "ncert_sections_neet.json"), `${JSON.stringify(output, null, 2)}\n`);
console.log(`Generated ${Object.keys(sections).length} chapters and ${Object.values(sections).flat().length} sections.`);

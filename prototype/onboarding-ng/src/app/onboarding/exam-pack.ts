import { CLASS11_SUBTOPICS } from './class11-subtopics';

/**
 * Exam pack model. Nothing here is NEET-specific — an SSC or JEE pack drops in
 * the same shape, mirroring syllabus_cgl.json's subject → unit → chapter tree.
 */
export interface Subtopic {
  id: string;
  name: string;
  custom?: true;
}

export interface Chapter {
  id: string;
  name: string;
  /** Section headings inside the chapter, when the pack carries them. */
  subtopics: Subtopic[];
  /** NCERT class the chapter sits in. */
  cls: 11 | 12;
  /** Planning estimate in hours. Derived, never sourced — see pack meta. */
  hours: number;
}

/** A chapter owned by the student rather than the bundled exam pack. */
export interface CustomChapter extends Chapter {
  subjectId: string;
  custom: true;
}

export interface Section {
  name: string;
  chapters: Chapter[];
}

export interface Subject {
  id: string;
  name: string;
  icon: string;
  /** Questions this subject carries in the paper. */
  questions: number;
  marks: number;
  sections: Section[];
}

export interface ExamPack {
  examId: string;
  displayName: string;
  totalQuestions: number;
  totalMarks: number;
  meta: { source: string; methodology: string[] };
  subjects: Subject[];
}

/** Botany and Zoology both draw on the NCERT Biology books. */
function packSubject(id: string): string {
  return id === 'botany' || id === 'zoology' ? 'biology' : id;
}

/** Section headings by subject and chapter, from the NCERT chapter PDFs. */
const SUBTOPICS: Record<string, string[]> = CLASS11_SUBTOPICS;

/** Total planning hours the pack is budgeted at, split by paper weight. */
const PACK_HOURS = 900;

function build(
  id: string,
  name: string,
  icon: string,
  questions: number,
  chapters: { name: string; cls: 11 | 12 }[],
): Subject {
  const share = (questions / 180) * PACK_HOURS;
  const per = Math.round((share / chapters.length) * 2) / 2;
  const make = (cls: 11 | 12) =>
    chapters
      .filter((c) => c.cls === cls)
      .map((c, i) => {
        const chapterId = `${id}.${cls}.${i + 1}`;
        return {
          id: chapterId,
          name: c.name,
          cls,
          hours: per,
          subtopics: (SUBTOPICS[`${packSubject(id)}::${c.name}`] ?? []).map((name, j) => ({
            id: `${chapterId}.${j + 1}`,
            name,
          })),
        };
      });

  return {
    id,
    name,
    icon,
    questions,
    marks: questions * 4,
    sections: [
      { name: 'Class 11', chapters: make(11) },
      { name: 'Class 12', chapters: make(12) },
    ].filter((s) => s.chapters.length > 0),
  };
}

const PHYSICS = build('physics', 'Physics', 'bolt', 45, [
  { name: 'Units and Measurements', cls: 11 },
  { name: 'Motion in a Straight Line', cls: 11 },
  { name: 'Motion in a Plane', cls: 11 },
  { name: 'Laws of Motion', cls: 11 },
  { name: 'Work, Energy and Power', cls: 11 },
  { name: 'System of Particles and Rotational Motion', cls: 11 },
  { name: 'Gravitation', cls: 11 },
  { name: 'Mechanical Properties of Solids', cls: 11 },
  { name: 'Mechanical Properties of Fluids', cls: 11 },
  { name: 'Thermal Properties of Matter', cls: 11 },
  { name: 'Thermodynamics', cls: 11 },
  { name: 'Kinetic Theory', cls: 11 },
  { name: 'Oscillations', cls: 11 },
  { name: 'Waves', cls: 11 },
  { name: 'Electric Charges and Fields', cls: 12 },
  { name: 'Electrostatic Potential and Capacitance', cls: 12 },
  { name: 'Current Electricity', cls: 12 },
  { name: 'Moving Charges and Magnetism', cls: 12 },
  { name: 'Magnetism and Matter', cls: 12 },
  { name: 'Electromagnetic Induction', cls: 12 },
  { name: 'Alternating Current', cls: 12 },
  { name: 'Electromagnetic Waves', cls: 12 },
  { name: 'Ray Optics and Optical Instruments', cls: 12 },
  { name: 'Wave Optics', cls: 12 },
  { name: 'Dual Nature of Radiation and Matter', cls: 12 },
  { name: 'Atoms', cls: 12 },
  { name: 'Nuclei', cls: 12 },
  { name: 'Semiconductor Electronics', cls: 12 },
]);

const CHEMISTRY = build('chemistry', 'Chemistry', 'science', 45, [
  { name: 'Some Basic Concepts of Chemistry', cls: 11 },
  { name: 'Structure of Atom', cls: 11 },
  { name: 'Classification of Elements and Periodicity', cls: 11 },
  { name: 'Chemical Bonding and Molecular Structure', cls: 11 },
  { name: 'Thermodynamics', cls: 11 },
  { name: 'Equilibrium', cls: 11 },
  { name: 'Redox Reactions', cls: 11 },
  { name: 'Organic Chemistry — Basic Principles and Techniques', cls: 11 },
  { name: 'Hydrocarbons', cls: 11 },
  { name: 'Solutions', cls: 12 },
  { name: 'Electrochemistry', cls: 12 },
  { name: 'Chemical Kinetics', cls: 12 },
  { name: 'The d- and f-Block Elements', cls: 12 },
  { name: 'Coordination Compounds', cls: 12 },
  { name: 'Haloalkanes and Haloarenes', cls: 12 },
  { name: 'Alcohols, Phenols and Ethers', cls: 12 },
  { name: 'Aldehydes, Ketones and Carboxylic Acids', cls: 12 },
  { name: 'Amines', cls: 12 },
  { name: 'Biomolecules', cls: 12 },
]);

/** Botany / Zoology is coaching convention, not an NCERT label — see meta. */
const BOTANY = build('botany', 'Botany', 'eco', 45, [
  { name: 'The Living World', cls: 11 },
  { name: 'Biological Classification', cls: 11 },
  { name: 'Plant Kingdom', cls: 11 },
  { name: 'Morphology of Flowering Plants', cls: 11 },
  { name: 'Anatomy of Flowering Plants', cls: 11 },
  { name: 'Cell : The Unit of Life', cls: 11 },
  { name: 'Biomolecules', cls: 11 },
  { name: 'Cell Cycle and Cell Division', cls: 11 },
  { name: 'Photosynthesis in Higher Plants', cls: 11 },
  { name: 'Respiration in Plants', cls: 11 },
  { name: 'Plant Growth and Development', cls: 11 },
  { name: 'Sexual Reproduction in Flowering Plants', cls: 12 },
  { name: 'Principles of Inheritance and Variation', cls: 12 },
  { name: 'Molecular Basis of Inheritance', cls: 12 },
  { name: 'Evolution', cls: 12 },
  { name: 'Biotechnology : Principles and Processes', cls: 12 },
  { name: 'Biotechnology and its Applications', cls: 12 },
  { name: 'Organisms and Populations', cls: 12 },
  { name: 'Ecosystem', cls: 12 },
  { name: 'Biodiversity and Conservation', cls: 12 },
]);

const ZOOLOGY = build('zoology', 'Zoology', 'pets', 45, [
  { name: 'Animal Kingdom', cls: 11 },
  { name: 'Structural Organisation in Animals', cls: 11 },
  { name: 'Breathing and Exchange of Gases', cls: 11 },
  { name: 'Body Fluids and Circulation', cls: 11 },
  { name: 'Excretory Products and their Elimination', cls: 11 },
  { name: 'Locomotion and Movement', cls: 11 },
  { name: 'Neural Control and Coordination', cls: 11 },
  { name: 'Chemical Coordination and Integration', cls: 11 },
  { name: 'Human Reproduction', cls: 12 },
  { name: 'Reproductive Health', cls: 12 },
  { name: 'Human Health and Disease', cls: 12 },
  { name: 'Microbes in Human Welfare', cls: 12 },
]);

export const PACK: ExamPack = {
  examId: 'neet_ug',
  displayName: 'NEET UG',
  totalQuestions: 180,
  totalMarks: 720,
  meta: {
    source: 'NCERT textbook contents (2026-27 reprint) + NTA NEET UG paper pattern',
    methodology: [
      'Chapters are the NCERT tables of contents for Classes 11 and 12, post-rationalisation.',
      'Questions per subject follow the NTA pattern: Physics 45, Chemistry 45, Biology 90.',
      'Botany / Zoology is the standard coaching split, not an NCERT designation.',
      'Hours are planning estimates: a 900h budget divided by paper weight, then by chapter.',
    ],
  },
  subjects: [PHYSICS, CHEMISTRY, BOTANY, ZOOLOGY],
};

export const ALL_CHAPTERS: Chapter[] = PACK.subjects.flatMap((s) =>
  s.sections.flatMap((sec) => sec.chapters),
);

/**
 * An exam is its subjects and their weight in the paper. Chapters are a bonus
 * some packs carry and others do not — a student sitting SSC CGL gets the four
 * papers and enters their own topics, which is the normal case, not a fallback.
 */
export interface ExamTemplate {
  id: string;
  displayName: string;
  /** Null when the pack ships no chapter list of its own. */
  pack: ExamPack | null;
  subjects: { id: string; name: string; icon: string; questions: number }[];
  source: string;
}

/** Subject shells for a pack with no chapters, so custom ones have a home. */
function shells(template: ExamTemplate): Subject[] {
  return template.subjects.map((s) => ({
    id: s.id,
    name: s.name,
    icon: s.icon,
    questions: s.questions,
    marks: s.questions * 4,
    sections: [{ name: 'My chapters', chapters: [] }],
  }));
}

export const EXAM_TEMPLATES: ExamTemplate[] = [
  {
    id: 'neet',
    displayName: 'NEET UG',
    pack: PACK,
    subjects: PACK.subjects.map((s) => ({
      id: s.id, name: s.name, icon: s.icon, questions: s.questions,
    })),
    source: PACK.meta.source,
  },
  {
    id: 'jee',
    displayName: 'JEE Main',
    pack: null,
    subjects: [
      { id: 'physics', name: 'Physics', icon: 'bolt', questions: 25 },
      { id: 'chemistry', name: 'Chemistry', icon: 'science', questions: 25 },
      { id: 'maths', name: 'Mathematics', icon: 'functions', questions: 25 },
    ],
    source: 'NTA JEE Main paper pattern. Chapters are yours to add.',
  },
  {
    id: 'cgl',
    displayName: 'SSC CGL',
    pack: null,
    subjects: [
      { id: 'quant', name: 'Quantitative Aptitude', icon: 'calculate', questions: 25 },
      { id: 'reasoning', name: 'Reasoning', icon: 'extension', questions: 25 },
      { id: 'english', name: 'English', icon: 'translate', questions: 25 },
      { id: 'gk', name: 'General Awareness', icon: 'public', questions: 25 },
    ],
    source: 'SSC CGL Tier 1 paper pattern. Chapters are yours to add.',
  },
];

/**
 * A subject by id, across every template. Chapter ids carry their subject as a
 * prefix, and a lookup that only knows the NEET pack cannot name a JEE or SSC
 * subject.
 */
export function subjectById(id: string): { id: string; name: string; questions: number } | undefined {
  for (const template of EXAM_TEMPLATES) {
    const found = template.subjects.find((s) => s.id === id);
    if (found) return found;
  }
  return undefined;
}

export function subjectNameOf(chapterId: string): string {
  return subjectById(chapterId.split('.')[0])?.name ?? '';
}

export function templateFor(examId: string): ExamTemplate {
  return EXAM_TEMPLATES.find((t) => t.id === examId) ?? EXAM_TEMPLATES[0];
}

/** The subjects an exam starts from, with or without a chapter list. */
export function templateSubjects(examId: string): Subject[] {
  const template = templateFor(examId);
  return template.pack ? template.pack.subjects : shells(template);
}

/** Merge user-owned chapters and names without mutating the frozen pack. */
export function mergedSubjects(
  custom: readonly CustomChapter[],
  names: ReadonlyMap<string, string>,
  customSubtopics: ReadonlyMap<string, Subtopic[]> = new Map(),
  subtopicNames: ReadonlyMap<string, string> = new Map(),
  hiddenSubtopics: ReadonlySet<string> = new Set(),
  /** False when the student chose to build their own list from nothing. */
  includePack = true,
  /** Which exam's subjects to start from. */
  examId = 'neet',
): Subject[] {
  const base = templateSubjects(examId);

  if (!includePack || !templateFor(examId).pack) {
    // The subjects survive as shells: they carry the paper's weighting and
    // give custom chapters somewhere to live.
    return base.map((subject) => ({
      ...subject,
      sections: [
        {
          name: 'My chapters',
          chapters: custom
            .filter((c) => c.subjectId === subject.id)
            .map((c) => ({ ...c, name: names.get(c.id) ?? c.name })),
        },
      ],
    }));
  }

  return base.map((subject) => ({
    ...subject,
    sections: subject.sections.map((section) => ({
      ...section,
      chapters: [
        ...section.chapters.map((chapter) => ({
          ...chapter,
          name: names.get(chapter.id) ?? chapter.name,
          subtopics: [
            ...chapter.subtopics
              .filter((topic) => !hiddenSubtopics.has(topic.id))
              .map((topic) => ({ ...topic, name: subtopicNames.get(topic.id) ?? topic.name })),
            ...(customSubtopics.get(chapter.id) ?? []).map((topic) => ({
              ...topic,
              name: subtopicNames.get(topic.id) ?? topic.name,
            })),
          ],
        })),
        ...custom
          .filter((chapter) => chapter.subjectId === subject.id && chapter.cls === Number(section.name.slice(-2)))
          .map((chapter) => ({
            ...chapter,
            name: names.get(chapter.id) ?? chapter.name,
            subtopics: (customSubtopics.get(chapter.id) ?? chapter.subtopics)
              .filter((topic) => !hiddenSubtopics.has(topic.id))
              .map((topic) => ({ ...topic, name: subtopicNames.get(topic.id) ?? topic.name })),
          })),
      ],
    })),
  }));
}

export function mergedChapters(
  custom: readonly CustomChapter[],
  names: ReadonlyMap<string, string>,
  customSubtopics: ReadonlyMap<string, Subtopic[]> = new Map(),
  subtopicNames: ReadonlyMap<string, string> = new Map(),
  hiddenSubtopics: ReadonlySet<string> = new Set(),
): Chapter[] {
  return mergedSubjects(custom, names, customSubtopics, subtopicNames, hiddenSubtopics).flatMap((subject) =>
    subject.sections.flatMap((section) => section.chapters),
  );
}

/** A chapter counts as done when every subtopic it lists is done. */
export function chapterIsDone(chapter: Chapter, done: ReadonlySet<string>): boolean {
  if (chapter.subtopics.length === 0) return done.has(chapter.id);
  return chapter.subtopics.every((t) => done.has(t.id));
}

export function subjectOf(chapterId: string): Subject {
  return PACK.subjects.find((s) => chapterId.startsWith(s.id + '.'))!;
}

/** Total planning hours in the pack — what the plan has to fit. */
export const PACK_TOTAL_HOURS = Math.round(
  ALL_CHAPTERS.reduce((n, c) => n + c.hours, 0),
);

/* ============================================================
   data.ts — exams, syllabus trees, scheduler, state shape.

   HARD PRODUCT RULE: this file contains syllabus topic NAMES and
   effort estimates only. Never add notes, questions, explanations,
   or solutions. The user brings their own study material.
   ============================================================ */

export type Exam = { id: string; name: string; meta: string };
export type Work = { id: string; t: string; s: string; wd: number; we: number };
export type Section = { n: string; q: number; t: [string, number][] };

export const EXAMS: Exam[] = [
  { id: 'cgl',  name: 'SSC CGL',   meta: '28.1 lakh applied · Tier 1 + Tier 2' },
  { id: 'chsl', name: 'SSC CHSL',  meta: '30 lakh applied · Tier 1 + Tier 2' },
  { id: 'ntpc', name: 'RRB NTPC',  meta: '1.22 crore applied · CBT 1 + CBT 2' },
  { id: 'neet', name: 'NEET UG',   meta: '22.8 lakh applied · single paper' },
  { id: 'jee',  name: 'JEE Main',  meta: '15 lakh applied · 2 sessions' },
  { id: 'ibps', name: 'IBPS PO',   meta: '12.4 lakh applied · Prelims + Mains' },
];

export const WORK: Work[] = [
  { id: 'ft',  t: 'Full-time aspirant', s: 'Preparation is the whole day', wd: 8, we: 8 },
  { id: 'job', t: 'Working a job',      s: 'Study before or after office', wd: 3, we: 8 },
  { id: 'col', t: 'In college',         s: 'Classes plus preparation',     wd: 4, we: 7 },
];

/* Syllabus trees keyed by exam id.
   Only 'cgl' is filled for the prototype. Others fall back to cgl. */
export const SYLLABUS: Record<string, Section[]> = {
  cgl: [
    { n: 'Quantitative Aptitude', q: 25, t: [
      ['Number Systems', 14], ['Percentage', 10], ['Ratio & Proportion', 12], ['Averages', 9],
      ['Profit, Loss & Discount', 16], ['Simple & Compound Interest', 12], ['Time & Work', 15],
      ['Time, Speed & Distance', 15], ['Mixture & Alligation', 9], ['Algebra', 22],
      ['Geometry', 26], ['Mensuration', 20], ['Trigonometry', 22], ['Data Interpretation', 14]] },
    { n: 'General Intelligence & Reasoning', q: 25, t: [
      ['Analogies', 7], ['Classification', 7], ['Series (number & figural)', 11], ['Coding-Decoding', 10],
      ['Blood Relations', 7], ['Direction & Distance', 6], ['Syllogism', 9], ['Statement & Conclusion', 9],
      ['Venn Diagrams', 6], ['Paper Folding & Cutting', 7], ['Mirror & Water Images', 5],
      ['Cube & Dice', 7], ['Non-verbal Series', 7]] },
    { n: 'General Awareness', q: 25, t: [
      ['Indian Polity & Constitution', 22], ['Modern History', 20], ['Ancient & Medieval History', 16],
      ['Geography — India', 18], ['Geography — World', 10], ['Indian Economy', 20],
      ['Physics, Chemistry, Biology basics', 24], ['Art & Culture', 12], ['Static GK', 14],
      ['Current Affairs (rolling)', 40]] },
    { n: 'English Comprehension', q: 25, t: [
      ['Reading Comprehension', 14], ['Error Spotting', 12], ['Sentence Improvement', 11],
      ['Fill in the Blanks', 8], ['Synonyms & Antonyms', 14], ['One Word Substitution', 12],
      ['Idioms & Phrases', 12], ['Spelling Correction', 7], ['Active & Passive Voice', 8],
      ['Direct & Indirect Speech', 8], ['Para Jumbles', 9], ['Cloze Test', 9]] },
  ],
};

/* ---------- state ---------- */
export type State = {
  exam: string;
  date: string | null;
  days: number;
  work: string;
  wd: number;
  we: number;
  place: string;
  done: Record<string, boolean>;   // "sectionIndex_topicIndex"
  missedDays: number;
};

export const DEFAULT_STATE: State = {
  exam: 'cgl',
  date: null,
  days: 118,
  work: 'col',
  wd: 4,
  we: 7,
  place: '',
  done: {},
  missedDays: 0,
};

/* ---------- syllabus helpers ---------- */
export const syllabusFor = (examId: string) => SYLLABUS[examId] || SYLLABUS.cgl;
export const examFor = (examId: string) => EXAMS.find(e => e.id === examId) || EXAMS[0];

export function rawHours(examId: string) {
  let h = 0;
  syllabusFor(examId).forEach(s => s.t.forEach(t => (h += t[1])));
  return h;
}

/* ---------- the scheduler ----------
   need      = raw syllabus hours * 1.28   (+28% for revision passes and mocks)
   available = full weeks * (5 weekdays + 2 weekend days) + leftover weekdays
   gap > 0   → shortfall.  gap <= 0 → buffer.
   ------------------------------------ */
export const REVISION_MULTIPLIER = 1.28;

export const needHours = (s: State) => Math.round(rawHours(s.exam) * REVISION_MULTIPLIER);

export function availableHours(s: State) {
  const weeks = Math.floor(s.days / 7), rem = s.days % 7;
  return Math.round(weeks * (5 * s.wd + 2 * s.we) + rem * s.wd);
}

export type Cushion = ReturnType<typeof cushion>;

export function cushion(s: State) {
  const need = needHours(s), have = availableHours(s), gap = need - have;
  return {
    need, have, gap,
    short: gap > 0,
    coverage: Math.min(100, Math.round((have / need) * 100)),
    extraPerDay: gap > 0 ? +(gap / s.days).toFixed(1) : 0,
    topicsToDrop: gap > 0 ? Math.ceil(gap / 14) : 0,
    daysToPush:  gap > 0 ? Math.ceil(gap / ((s.wd + s.we) / 2)) : 0,
    bufferDays:  gap <= 0 ? Math.floor(Math.abs(gap) / s.wd) : 0,
  };
}

/* ---------- misc ---------- */
export function daysUntil(dateStr: string) {
  return Math.max(1, Math.round((new Date(dateStr).getTime() - new Date().setHours(0, 0, 0, 0)) / 864e5));
}
export const isWeekend = (d?: Date) => [0, 6].includes((d || new Date()).getDay());
export const todayBudget = (s: State) => (isWeekend() ? s.we : s.wd);

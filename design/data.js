/* data.js — exams, syllabus, scheduler, plan state (localStorage key: plan) */

import { CGL, sectionHours } from './syllabus-cgl.js';

export const EXAMS = [
  { id: 'cgl', name: 'SSC CGL', meta: '28.1 lakh applied · Tier 1 + Tier 2' },
  { id: 'chsl', name: 'SSC CHSL', meta: '30 lakh applied · Tier 1 + Tier 2' },
  { id: 'ntpc', name: 'RRB NTPC', meta: '1.22 crore applied · CBT 1 + CBT 2' },
  { id: 'neet', name: 'NEET UG', meta: '22.8 lakh applied · single paper' },
  { id: 'jee', name: 'JEE Main', meta: '15 lakh applied · 2 sessions' },
  { id: 'ibps', name: 'IBPS PO', meta: '12.4 lakh applied · Prelims + Mains' },
];

export const WORK = [
  { id: 'ft', t: 'Full-time aspirant', s: 'Preparation is the whole day', wd: 8, we: 8 },
  { id: 'job', t: 'Working a job', s: 'Study before or after office', wd: 3, we: 8 },
  { id: 'col', t: 'In college', s: 'Classes plus preparation', wd: 4, we: 7 },
];

export const DEFAULT_STATE = {
  exam: 'cgl',
  date: null,
  days: 118,
  work: 'col',
  wd: 4,
  we: 7,
  place: '',
  done: {},
  missedDays: 0,
  blocksDone: {},
  activeSubjects: null,
};

const REVISION_MULTIPLIER = 1.28;

export function hasPlan() {
  try {
    return localStorage.getItem('plan') !== null;
  } catch (_) {
    return false;
  }
}

export function loadState() {
  try {
    const raw = localStorage.getItem('plan');
    if (!raw) return null;
    return { ...DEFAULT_STATE, ...JSON.parse(raw) };
  } catch (_) {
    return null;
  }
}

export function saveState(s) {
  try {
    localStorage.setItem('plan', JSON.stringify(s));
  } catch (_) {}
}

export function seedDemoPlan() {
  const plan = {
    ...DEFAULT_STATE,
    exam: 'cgl',
    date: new Date(Date.now() + 118 * 86400000).toISOString().slice(0, 10),
    days: 118,
    place: 'Terrace',
    blocksDone: { 0: true, 2: true, 5: true },
  };
  saveState(plan);
  return plan;
}

export function syllabusFor(examId) {
  return examId === 'cgl' ? CGL.tier1 : CGL.tier1;
}

export function examFor(examId) {
  return EXAMS.find((e) => e.id === examId) || EXAMS[0];
}

export function rawHours(examId) {
  return sectionHours(syllabusFor(examId));
}

export function needHours(s) {
  return Math.round(rawHours(s.exam) * REVISION_MULTIPLIER);
}

export function availableHours(s) {
  const weeks = Math.floor(s.days / 7);
  const rem = s.days % 7;
  return Math.round(weeks * (5 * s.wd + 2 * s.we) + rem * s.wd);
}

export function cushion(s) {
  const need = needHours(s);
  const have = availableHours(s);
  const gap = need - have;
  return {
    need,
    have,
    gap,
    short: gap > 0,
    coverage: Math.min(100, Math.round((have / need) * 100)),
    extraPerDay: gap > 0 ? +(gap / s.days).toFixed(1) : 0,
    topicsToDrop: gap > 0 ? Math.ceil(gap / 14) : 0,
    daysToPush: gap > 0 ? Math.ceil(gap / ((s.wd + s.we) / 2)) : 0,
    bufferDays: gap <= 0 ? Math.floor(Math.abs(gap) / s.wd) : 0,
  };
}

export function isWeekend(d) {
  return [0, 6].includes((d || new Date()).getDay());
}

export function todayBudget(s) {
  return isWeekend() ? s.we : s.wd;
}

/** Today's time blocks — grouped morning / afternoon / evening */
export function todayBlocks() {
  return [
    {
      id: 0,
      group: 'morning',
      time: '06:30',
      title: 'Geometry — Triangles',
      sub: 'Your book, §4.1–4.4 · 90 min',
      tag: 'read',
      subject: 'quant',
      mins: 90,
    },
    {
      id: 'break1',
      group: 'morning',
      time: '08:00',
      break: true,
      mins: 15,
    },
    {
      id: 2,
      group: 'morning',
      time: '08:15',
      title: 'Geometry — 40 practice questions',
      sub: 'Previous-year set · 75 min',
      tag: 'prac',
      subject: 'quant',
      mins: 75,
    },
    {
      id: 3,
      group: 'afternoon',
      time: '14:00',
      title: 'Revision: Percentage',
      sub: 'Done 6 days ago · 30 min',
      tag: 'rev',
      subject: 'quant',
      mins: 30,
    },
    {
      id: 4,
      group: 'afternoon',
      time: '15:30',
      title: 'Current Affairs',
      sub: 'Rolling topic · 30 min daily',
      tag: 'read',
      subject: 'ga',
      mins: 30,
    },
    {
      id: 5,
      group: 'evening',
      time: '18:30',
      title: 'Reasoning — Series',
      sub: 'Module 3 · 45 min',
      tag: 'prac',
      subject: 'reasoning',
      mins: 45,
    },
  ];
}

export const BLOCK_GROUPS = [
  { id: 'morning', label: 'Morning' },
  { id: 'afternoon', label: 'Afternoon' },
  { id: 'evening', label: 'Evening' },
];

export const TAG_LABELS = { read: 'Read', prac: 'Practice', rev: 'Revise' };

/** Tier-1 subjects — shown as editable chips on Home */
export const SUBJECTS = [
  { id: 'quant', label: 'Quant', short: 'Q' },
  { id: 'reasoning', label: 'Reasoning', short: 'R' },
  { id: 'ga', label: 'GA', short: 'G' },
  { id: 'english', label: 'English', short: 'E' },
];

export function defaultActiveSubjects() {
  return SUBJECTS.map((s) => s.id);
}

export function doneMinutes(blocks, blocksDone) {
  return blocks
    .filter((b) => !b.break && blockIsDone(blocksDone, b.id))
    .reduce((sum, b) => sum + (b.mins || 0), 0);
}

export function blockIsDone(blocksDone, id) {
  if (!blocksDone) return false;
  return !!(blocksDone[id] ?? blocksDone[String(id)]);
}

export function formatDayLine(s) {
  const t = new Date();
  const date = t.toLocaleDateString('en-IN', {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
  });
  const doneHrs = (doneMinutes(todayBlocks(), s.blocksDone || {}) / 60).toFixed(1);
  const parts = [date];
  if (s.place) parts.push(s.place);
  parts.push(`${doneHrs} hrs done`);
  return parts.join(' · ');
}

/* syllabus-sources.js — where each breakdown comes from */

export const SOURCES = {
  ssc: {
    id: 'ssc',
    short: 'SSC',
    label: 'SSC Official Notification',
    detail: 'Indicative syllabus Paras 13.9 (Tier-I) & 13.10 (Tier-II)',
    url: 'https://ssc.gov.in',
  },
  kiran: {
    id: 'kiran',
    short: 'Kiran',
    label: 'Kiran SSC Mathematics',
    detail: 'Chapter-wise & type-wise previous year questions (PYQ)',
  },
  rakesh: {
    id: 'rakesh',
    short: 'Rakesh Y.',
    label: 'Rakesh Yadav Class Notes / Book',
    detail: 'Arithmetic + Advanced Maths — standard SSC coaching sequence',
  },
  rs: {
    id: 'rs',
    short: 'RS Agg.',
    label: 'RS Aggarwal Quantitative Aptitude',
    detail: 'Chapter structure used across SSC / banking prep',
  },
  adda: {
    id: 'adda',
    short: 'Adda',
    label: 'Adda247 / SSC Adda',
    detail: 'Topic-wise weightage & chapter lists from coaching analysis',
  },
  lucent: {
    id: 'lucent',
    short: 'Lucent',
    label: 'Lucent General Knowledge',
    detail: 'Static GK — History, Polity, Geography, Science, Economy',
  },
  spbakshi: {
    id: 'spbakshi',
    short: 'SP Bakshi',
    label: 'SP Bakshi Objective General English',
    detail: 'Arihant — grammar, vocabulary, comprehension chapters',
  },
  plinth: {
    id: 'plinth',
    short: 'Plinth',
    label: 'Plinth to Paramount Reasoning',
    detail: 'Verbal & non-verbal reasoning chapter sequence',
  },
  pyq: {
    id: 'pyq',
    short: 'PYQ',
    label: 'SSC CGL Previous Year Papers',
    detail: 'Topic tags from 2019–2024 CGL Tier-I papers (coaching compilations)',
  },
};

/** Resolve source ids to display objects */
export function resolveSources(ids) {
  if (!ids || !ids.length) return [];
  return ids.map((id) => SOURCES[id]).filter(Boolean);
}

export const SYLLABUS_METHODOLOGY = {
  title: 'How this syllabus is built',
  steps: [
    'SSC official indicative list (Para 13.9 / 13.10) — legal exam scope',
    'Split into study chapters matching Kiran / Rakesh Yadav / RS Aggarwal maths books',
    'Reasoning grouped per Plinth-to-Paramount + Adda247 weightage chapters',
    'GA from Lucent GK chapter list + SSC PYQ topic tags',
    'English from SP Bakshi chapter sequence + SSC Tier-I question types',
    'Hours are planning estimates — not from any book',
  ],
};

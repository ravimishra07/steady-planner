/* syllabus-progress.js — reads the syllabus tick state that views/syllabus.js
   writes (localStorage `syllabus_ui`, leaf keys `t1_<section>_<topic>[_<child>…]`)
   and turns it into hours. Partial topics count proportionally, so a topic with
   3 of 6 leaves ticked contributes half its hours. */

import { CGL, topicHours } from './syllabus-cgl.js';

function loadDone() {
  try {
    const raw = localStorage.getItem('syllabus_ui');
    if (!raw) return {};
    return JSON.parse(raw).done || {};
  } catch (_) {
    return {};
  }
}

function leafKeys(node, pathKey) {
  if (!node.c || !node.c.length) return [pathKey];
  return node.c.flatMap((child, ci) => leafKeys(child, `${pathKey}_${ci}`));
}

/** Per-section and overall progress, in hours and topic counts. */
export function syllabusProgress() {
  const done = loadDone();
  const sections = CGL.tier1.map((sec, si) => {
    let hoursDone = 0;
    let topicsDone = 0;
    const hoursTotal = sec.t.reduce((h, t) => h + topicHours(t), 0);

    sec.t.forEach((topic, ti) => {
      const leaves = leafKeys(topic, `t1_${si}_${ti}`);
      const n = leaves.filter((k) => done[k]).length;
      const frac = leaves.length ? n / leaves.length : 0;
      hoursDone += topicHours(topic) * frac;
      if (frac >= 1) topicsDone += 1;
    });

    return {
      name: sec.n,
      questions: sec.q,
      topicsTotal: sec.t.length,
      topicsDone,
      hoursTotal,
      hoursDone: Math.round(hoursDone),
      pct: hoursTotal ? Math.round((hoursDone / hoursTotal) * 100) : 0,
    };
  });

  const hoursTotal = sections.reduce((h, s) => h + s.hoursTotal, 0);
  const hoursDone = sections.reduce((h, s) => h + s.hoursDone, 0);

  return {
    sections,
    hoursTotal,
    hoursDone,
    topicsTotal: sections.reduce((n, s) => n + s.topicsTotal, 0),
    topicsDone: sections.reduce((n, s) => n + s.topicsDone, 0),
    pct: hoursTotal ? Math.round((hoursDone / hoursTotal) * 100) : 0,
  };
}

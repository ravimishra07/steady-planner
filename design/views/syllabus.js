import { CGL, tierMeta, SYLLABUS_META, topicHours, SOURCES } from '../syllabus-cgl.js';

/** Syllabus view — ported from syllabus.html for app shell */
export function mountSyllabus(root, { navigate }) {
  root.className = 'view view-syllabus';
  root.innerHTML = `
    <header class="header" id="hdr"></header>
    <div class="tiers" id="tiers"></div>
    <div class="scroll view-scroll" id="list"></div>
    <div class="method" id="method"></div>
    <p class="source" id="source"></p>
    <div class="bottom">
      <button type="button" class="cta" id="rebuild">Rebuild plan</button>
      <p class="hint" id="hint"></p>
    </div>`;

  const $ = (id) => root.querySelector('#' + id);
const REV = 1.28;
    const PLAN_AVAILABLE = 568;
    const CHECK = '<svg width="16" height="16" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    const CHECK_SM = '<svg width="11" height="11" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    const SEED_DONE = [
      't1_0_0_0_0', 't1_0_0_0_1', 't1_0_0_1_0',
      't1_1_0_0_0', 't1_1_0_1_0', 't1_1_1_0_0',
      't1_2_0_0_0', 't1_2_0_1_0', 't1_2_1_0_0',
      't1_3_0_0_0', 't1_3_0_1_0', 't1_3_1_0_0',
    ];

    function load() {
      const base = {
        tier2: false,
        expanded: ['t1_0'],
        expandedSub: ['t1_0_0'],
        done: Object.fromEntries(SEED_DONE.map((k) => [k, true])),
      };
      try {
        const raw = localStorage.getItem('syllabus_ui');
        if (raw) {
          const parsed = JSON.parse(raw);
          return {
            ...base,
            ...parsed,
            expandedSub: parsed.expandedSub || base.expandedSub,
            expanded: parsed.expanded || base.expanded,
          };
        }
      } catch (_) {}
      return base;
    }

    function save() {
      try { localStorage.setItem('syllabus_ui', JSON.stringify(state)); } catch (_) {}
    }

    let state = load();

    function activeSections() {
      const rows = CGL.tier1.map((s, i) => ({ tier: 't1', i, s }));
      if (state.tier2) {
        CGL.tier2.forEach((s, i) => rows.push({ tier: 't2', i, s }));
      }
      return rows;
    }

    function key(tier, si, ti) {
      return `${tier}_${si}_${ti}`;
    }

    function sectionHoursSum(sec) {
      return sec.t.reduce((h, topic) => h + topicHours(topic), 0);
    }

    /** Collect deepest leaf keys under a node */
    function collectLeafKeys(node, pathKey) {
      if (!node.c || !node.c.length) return [pathKey];
      return node.c.flatMap((child, ci) => collectLeafKeys(child, `${pathKey}_${ci}`));
    }

    function checkState(leafKeys) {
      const done = leafKeys.filter((k) => state.done[k]).length;
      if (done === 0) return 'none';
      if (done === leafKeys.length) return 'all';
      return 'partial';
    }

    function renderCheckBox(leafKeys, small) {
      const st = checkState(leafKeys);
      const sm = small ? ' sm' : '';
      const cls = st === 'all' ? ' on' : st === 'partial' ? ' partial' : '';
      const icon = st === 'all' ? (small ? CHECK_SM : CHECK) : '';
      return `<button type="button" class="box${sm}${cls}" data-leaves="${leafKeys.join(',')}" aria-label="Toggle done" aria-pressed="${st === 'all'}">${icon}</button>`;
    }

    function nameClass(leafKeys) {
      const st = checkState(leafKeys);
      if (st === 'all') return ' done';
      if (st === 'partial') return ' partial';
      return '';
    }

    function toggleLeaves(leafKeys) {
      const allDone = leafKeys.every((k) => state.done[k]);
      leafKeys.forEach((k) => {
        if (allDone) delete state.done[k];
        else state.done[k] = true;
      });
    }

    function topicFraction(topic, topicKey) {
      const leaves = collectLeafKeys(topic, topicKey);
      const done = leaves.filter((k) => state.done[k]).length;
      return { leaves, done, total: leaves.length, frac: leaves.length ? done / leaves.length : (state.done[topicKey] ? 1 : 0) };
    }

    function renderChildren(children, parentKey) {
      if (!children || !children.length) return '';
      return children.map((child, ci) => {
        const k = `${parentKey}_${ci}`;
        const hasKids = child.c && child.c.length;
        const leafKeys = collectLeafKeys(child, k);
        if (!hasKids) {
          return `<div class="leaf-row">
            ${renderCheckBox(leafKeys, true)}
            <div class="leaf-txt${nameClass(leafKeys)}">
              ${esc(child.n)}
              ${srcBadges(child.src)}
            </div>
          </div>`;
        }
        const open = (state.expandedSub || []).includes(k);
        return `<div class="subbranch${open ? ' open' : ''}" data-branch="${k}">
          <div class="subrow">
            ${renderCheckBox(leafKeys, true)}
            <button type="button" class="subhead" data-sub="${k}" aria-expanded="${open}">
              <span class="chev" aria-hidden="true">›</span>
              <span class="nm${nameClass(leafKeys)}">${esc(child.n)}</span>
              ${srcBadges(child.src)}
              ${child.r ? `<span class="ref">${esc(child.r)}</span>` : ''}
            </button>
          </div>
          <div class="subtree">${renderChildren(child.c, k)}</div>
        </div>`;
      }).join('');
    }

    function totals() {
      const rows = activeSections();
      let rawTotal = 0;
      let doneHrs = 0;
      rows.forEach(({ tier, i, s }) => {
        s.t.forEach((topic, ti) => {
          const tk = key(tier, i, ti);
          const hrs = topicHours(topic);
          rawTotal += hrs;
          doneHrs += hrs * topicFraction(topic, tk).frac;
        });
      });
      const need = Math.round(rawTotal * REV);
      const remaining = Math.max(0, need - doneHrs);
      const gap = Math.max(0, need - PLAN_AVAILABLE);
      const behind = gap > 0
        ? Math.min(remaining, Math.round(gap * (remaining / Math.max(need, 1))))
        : 0;
      const planned = Math.max(0, remaining - behind);
      return { rawTotal, need, doneHrs, remaining, behind, planned };
    }

    function sectionDoneCount(tier, si, s) {
      let sum = 0;
      s.t.forEach((topic, ti) => {
        sum += topicFraction(topic, key(tier, si, ti)).frac;
      });
      return sum;
    }

    function formatSectionCount(n, total) {
      if (Math.abs(n - Math.round(n)) < 0.05) return `${Math.round(n)} / ${total}`;
      return `${n.toFixed(1)} / ${total}`;
    }

    function migrateLegacyDone() {
      activeSections().forEach(({ tier, i, s }) => {
        s.t.forEach((topic, ti) => {
          const tk = key(tier, i, ti);
          const topicKey = `${tier}_${i}_${ti}`;
          if (state.done[tk] && topic.c && topic.c.length) {
            collectLeafKeys(topic, topicKey).forEach((lk) => { state.done[lk] = true; });
            delete state.done[tk];
          }
        });
      });
    }

    function esc(s) {
      return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    }

    function srcBadges(ids, title) {
      if (!ids || !ids.length) return '';
      const tags = ids.map((id) => {
        const s = SOURCES[id];
        if (!s) return '';
        const tip = esc(s.label + ' — ' + s.detail);
        return `<span class="src-tag" title="${tip}">${esc(s.short)}</span>`;
      }).join('');
      return `<span class="src-tags"${title ? ` title="${esc(title)}"` : ''}>${tags}</span>`;
    }

    function renderHeader() {
      const t = totals();
      $('hdr').innerHTML = `
        <h1>Syllabus</h1>
        <p class="meta">${t.doneHrs} hrs done · ${t.remaining} to go · SSC CGL</p>
        <div class="prog" role="img" aria-label="Progress">
          <span class="done" style="flex-grow:${t.doneHrs}"></span>
          <span class="behind" style="flex-grow:${t.behind}"></span>
          <span class="planned" style="flex-grow:${Math.max(t.planned, 1)}"></span>
        </div>
        <div class="legend">
          <span><i class="ld"></i>Done</span>
          <span><i class="lb"></i>Behind</span>
          <span><i class="lp"></i>Planned</span>
        </div>`;
    }

    function renderTiers() {
      const m1 = tierMeta(CGL.tier1);
      const m2 = tierMeta(CGL.tier2);
      $('tiers').innerHTML = `
        <div class="tier on" aria-current="true">
          <span class="mark">${CHECK}</span>
          <b>Tier 1</b>
          <span>${m1.sections} sections · ${m1.hours}h</span>
        </div>
        <button type="button" class="tier tappable${state.tier2 ? ' on' : ''}" id="tier2btn" aria-pressed="${state.tier2}">
          <span class="mark">${state.tier2 ? CHECK : ''}</span>
          <b>Tier 2</b>
          <span>${m2.sections} sections · ${m2.hours}h</span>
        </button>`;
      $('tier2btn').addEventListener('click', () => {
        state.tier2 = !state.tier2;
        save();
        render();
      });
    }

    function renderList() {
      const html = activeSections().map(({ tier, i, s }) => {
        const id = `${tier}_${i}`;
        const open = state.expanded.includes(id);
        const doneFrac = sectionDoneCount(tier, i, s);
        const total = s.t.length;
        const hrs = sectionHoursSum(s);
        const pct = total ? Math.round((doneFrac / total) * 100) : 0;
        const prefix = tier === 't2' ? 'TIER 2 · ' : '';
        const topics = s.t.map((topic, ti) => {
          const topicKey = `${tier}_${i}_${ti}`;
          const leafKeys = collectLeafKeys(topic, topicKey);
          const hrs = topicHours(topic);
          const hasKids = topic.c && topic.c.length;
          const subOpen = (state.expandedSub || []).includes(topicKey);
          const subtree = hasKids
            ? `<div class="subtree-wrap${subOpen ? ' open' : ''}">${renderChildren(topic.c, topicKey)}</div>`
            : '';
          return `<div class="tp${hasKids ? ' has-sub' : ''}">
            ${renderCheckBox(leafKeys, false)}
            <span class="nm-wrap">
              <span class="nm${nameClass(leafKeys)}">${esc(topic.n)}</span>
              ${srcBadges(topic.src)}
            </span>
            <span class="hrs">${hrs}h</span>
            ${hasKids ? `<button type="button" class="subtoggle" data-sub="${topicKey}" aria-expanded="${subOpen}" aria-label="Show subtopics">${subOpen ? '▾' : '›'}</button>` : ''}
          </div>${subtree}`;
        }).join('');
        return `<article class="sec${open ? ' open' : ''}" data-sec="${id}">
          <button type="button" class="sechead">
            <span class="info">
              <b>${esc(s.n)}</b>
              <em>${prefix}${hrs}h · ${s.q} questions${s.r ? ' · ' + esc(s.r) : ''}</em>
            </span>
            <span class="count"><span class="dn">${doneFrac % 1 < 0.05 ? Math.round(doneFrac) : doneFrac.toFixed(1)}</span> / ${total}</span>
            <span class="chev" aria-hidden="true">›</span>
          </button>
          <div class="topics">${topics}<button type="button" class="addrow">+ Add a topic</button></div>
          <div class="secbar"><i style="width:${pct}%"></i></div>
        </article>`;
      }).join('');
      const el = $('list');
      el.innerHTML = html;

      el.querySelectorAll('.sechead').forEach((btn) => {
        btn.addEventListener('click', () => {
          const id = btn.closest('.sec').dataset.sec;
          const ix = state.expanded.indexOf(id);
          if (ix >= 0) state.expanded.splice(ix, 1);
          else state.expanded.push(id);
          save();
          renderList();
          renderHeader();
          renderHint();
        });
      });

      el.querySelectorAll('.box[data-leaves]').forEach((btn) => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const leafKeys = btn.dataset.leaves.split(',').filter(Boolean);
          toggleLeaves(leafKeys);
          save();
          renderList();
          renderHeader();
          renderHint();
        });
      });

      function toggleSub(id) {
        if (!state.expandedSub) state.expandedSub = [];
        const ix = state.expandedSub.indexOf(id);
        if (ix >= 0) state.expandedSub.splice(ix, 1);
        else state.expandedSub.push(id);
        save();
        renderList();
      }

      el.querySelectorAll('.subtoggle, .subhead').forEach((btn) => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          toggleSub(btn.dataset.sub);
        });
      });
    }

    function renderHint() {
      const t = totals();
      const el = $('hint');
      el.textContent = state.tier2
        ? `Planning for both tiers · ${t.rawTotal} hrs of syllabus`
        : 'Tier 1 only · turn on Tier 2 when you clear it';
    }

    function renderMethodology() {
      const m = SYLLABUS_META.methodology;
      if (!m) return;
      $('method').innerHTML = `
        <b>${esc(m.title)}</b>
        <ol>${m.steps.map((s) => `<li>${esc(s)}</li>`).join('')}</ol>`;
    }

    function renderSource() {
      const legend = Object.values(SOURCES).map((s) =>
        `<span><span class="src-tag">${esc(s.short)}</span> ${esc(s.label)}</span>`,
      ).join('');
      $('source').innerHTML = `
        <em>${esc(SYLLABUS_META.source)}</em>
        <div class="src-legend">${legend}</div>`;
    }

    function render() {
      renderHeader();
      renderTiers();
      renderList();
      renderHint();
      renderMethodology();
      renderSource();
    }

    $('rebuild').addEventListener('click', () => {
      $('rebuild').textContent = 'Plan updated';
      setTimeout(() => { $('rebuild').textContent = 'Rebuild plan'; }, 1400);
    });

    migrateLegacyDone();
    render();
  
  return () => {};
}

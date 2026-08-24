/* canvas-flow.js — click-by-click prototype walkthrough */

import { scrollToScreen } from './canvas-nav.js';

/**
 * @param {object} config
 * @param {HTMLElement} config.scrollRoot
 * @param {{ goToScreen: Function, setActiveId: Function }} [config.nav]
 * @param {Array<{ id: string, label: string, action?: string }>} config.steps
 */
export function mountFlowWalkthrough(config) {
  const { scrollRoot, nav, steps } = config;
  let stepIndex = 0;

  const flowBar = document.createElement('div');
  flowBar.className = 'flow-bar';
  flowBar.innerHTML = `
    <button type="button" class="flow-btn" id="flow-prev" aria-label="Previous screen">← Prev</button>
    <div class="flow-mid">
      <span class="flow-step" id="flow-step">1 / ${steps.length}</span>
      <span class="flow-hint" id="flow-hint"></span>
    </div>
    <button type="button" class="flow-btn flow-btn-primary" id="flow-next" aria-label="Next screen">Next →</button>
  `;
  scrollRoot.appendChild(flowBar);

  const hintEl = flowBar.querySelector('#flow-hint');
  const stepEl = flowBar.querySelector('#flow-step');
  const prevBtn = flowBar.querySelector('#flow-prev');
  const nextBtn = flowBar.querySelector('#flow-next');

  function allColumns() {
    return steps.map((s) => document.getElementById(s.id)).filter(Boolean);
  }

  function goStep(index) {
    const step = steps[Math.max(0, Math.min(index, steps.length - 1))];
    if (!step) return;

    allColumns().forEach((col) => {
      col.classList.remove('flow-active', 'flow-dim');
      if (col.id === step.id) col.classList.add('flow-active');
      else col.classList.add('flow-dim');
    });

    stepIndex = index;
    stepEl.textContent = `${index + 1} / ${steps.length}`;
    hintEl.textContent = step.action || step.label;
    prevBtn.disabled = index <= 0;
    nextBtn.textContent = index >= steps.length - 1 ? 'Done' : 'Next →';

    const target = document.getElementById(step.id);
    if (target) {
      if (nav?.setActiveId) nav.setActiveId(step.id);
      scrollToScreen(scrollRoot, target);
    }
  }

  function next() {
    if (stepIndex < steps.length - 1) goStep(stepIndex + 1);
  }

  function prev() {
    if (stepIndex > 0) goStep(stepIndex - 1);
  }

  prevBtn.addEventListener('click', (e) => { e.stopPropagation(); prev(); });
  nextBtn.addEventListener('click', (e) => { e.stopPropagation(); next(); });

  document.addEventListener('keydown', (e) => {
    if (e.target.matches('input, textarea, select, iframe')) return;
    if (e.key === 'ArrowRight' || e.key === 'Enter') { e.preventDefault(); next(); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); prev(); }
  });

  scrollRoot.addEventListener('dblclick', (e) => {
    if (e.target.closest('.flow-bar')) return;
    const col = e.target.closest('.screen-col.flow-active');
    if (col) next();
  });

  goStep(0);

  return { next, prev, goStep, getStep: () => stepIndex };
}

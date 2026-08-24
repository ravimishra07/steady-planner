const BACK = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>';

export function mountRebalance(root, { navigate }) {
  root.className = 'view view-rebalance';
  root.innerHTML = `
    <div class="view-scroll" style="padding-top:4px">
      <div class="backrow" style="display:flex;align-items:center;gap:12px;padding:0 var(--sam-screen-h) var(--sam-space-md)">
        <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
        <span class="nav-title">Rebalance</span>
      </div>
      <div style="padding:0 var(--sam-screen-h)">
        <p class="eyebrow">Since last week</p>
        <div class="big">+18 hrs</div>
        <p class="sub" style="font-size:var(--sam-fs-lg);color:var(--sam-text-secondary);line-height:var(--sam-lh-relaxed)">You fell further behind. Pick how to recover.</p>
        <div class="list" style="margin-top:var(--sam-space-lg);display:flex;flex-direction:column;gap:var(--sam-space-md)">
          <button type="button" class="fixcard opt"><span class="metric">+1.5h</span><span class="ftxt"><b>Add to weekdays</b><span>5.5 hrs/day for next 30 days</span></span><span class="chev">&#8250;</span></button>
          <button type="button" class="fixcard opt"><span class="metric">&minus;6</span><span class="ftxt"><b>Skip 6 low-yield topics</b><span>Saves ~22 hrs</span></span><span class="chev">&#8250;</span></button>
          <button type="button" class="fixcard opt"><span class="metric">Sat</span><span class="ftxt"><b>Use Saturday catch-up</b><span>+4 hrs this weekend only</span></span><span class="chev">&#8250;</span></button>
        </div>
      </div>
    </div>
    <div class="view-foot"><button type="button" class="cta" id="apply-reb">Apply changes</button></div>`;

  const apply = root.querySelector('#apply-reb');
  const opts = [...root.querySelectorAll('.fixcard.opt')];

  // Nothing is chosen until the user picks — the CTA stays disabled so the
  // screen cannot be completed without a decision.
  apply.disabled = true;
  apply.textContent = 'Pick an option';

  opts.forEach((btn) => {
    btn.setAttribute('aria-pressed', 'false');
    btn.addEventListener('click', () => {
      opts.forEach((b) => {
        const on = b === btn;
        b.classList.toggle('sel', on);
        b.setAttribute('aria-pressed', String(on));
      });
      apply.disabled = false;
      apply.textContent = 'Apply this change';
    });
  });

  root.querySelector('.back-chev').addEventListener('click', () => navigate('today'));
  apply.addEventListener('click', () => {
    if (apply.disabled) return;
    apply.textContent = 'Applied';
    apply.disabled = true;
    setTimeout(() => navigate('today'), 600);
  });

  return () => {};
}

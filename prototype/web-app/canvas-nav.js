/* canvas-nav.js — left sidebar + scroll-to + flow walkthrough */

/**
 * Scroll canvas to a screen column
 * @param {HTMLElement} scrollRoot
 * @param {HTMLElement} target
 */
export function scrollToScreen(scrollRoot, target) {
  if (!scrollRoot || !target) return;
  const rootRect = scrollRoot.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  const left = scrollRoot.scrollLeft + (targetRect.left - rootRect.left) - 80;
  const top = scrollRoot.scrollTop + (targetRect.top - rootRect.top) - 60;
  scrollRoot.scrollTo({
    left: Math.max(0, left),
    top: Math.max(0, top),
    behavior: 'smooth',
  });
}

/**
 * @param {HTMLElement} navRoot
 * @param {Array<{ id: string, label: string, num?: string|number, sub?: string }>} items
 * @param {{ scrollRoot?: HTMLElement, onSelect?: (id: string) => void }} [opts]
 * @returns {{ goToScreen: (id: string) => void, setActiveId: (id: string) => void }}
 */
export function mountScreenNav(navRoot, items, opts = {}) {
  const list = navRoot.querySelector('.nav-list');
  const scrollRoot = opts.scrollRoot || document.querySelector('.canvas-main');
  let activeBtn = null;

  function setActiveId(id) {
    const btn = list?.querySelector(`[data-target="${id}"]`);
    if (btn) setActive(btn);
  }

  function setActive(btn) {
    if (activeBtn) activeBtn.classList.remove('on');
    activeBtn = btn;
    if (activeBtn) activeBtn.classList.add('on');
    if (activeBtn?.dataset.target && opts.onSelect) {
      opts.onSelect(activeBtn.dataset.target);
    }
  }

  function goToScreen(id) {
    const target = document.getElementById(id);
    if (!target) return;
    setActiveId(id);
    scrollToScreen(scrollRoot, target);
    if (opts.onSelect) opts.onSelect(id);
  }

  if (!list) return { goToScreen, setActiveId };

  list.innerHTML = items.map((item) => {
    const num = item.num != null ? String(item.num).padStart(2, '0') : '';
    const sub = item.sub ? `<span class="sub">${item.sub}</span>` : '';
    return `<button type="button" class="nav-item" data-target="${item.id}">
      <span class="num">${num}</span>
      <span class="lbl">${item.label}${sub}</span>
    </button>`;
  }).join('');

  list.querySelectorAll('.nav-item').forEach((btn) => {
    btn.addEventListener('click', () => {
      goToScreen(btn.dataset.target);
    });
  });

  const first = list.querySelector('.nav-item');
  if (first) setActive(first);

  return { goToScreen, setActiveId };
}

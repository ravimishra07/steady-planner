/* Minimal stub so .dc.html artboards render in a normal browser (iframe / local canvas). */
window.DCLogic = class {
  renderVals() { return {}; }
};

function hoistHelmet() {
  const dc = document.querySelector('x-dc');
  if (!dc) return;
  const helmet = dc.querySelector('helmet');
  if (!helmet) return;
  helmet.querySelectorAll('link[rel="stylesheet"]').forEach((l) => {
    document.head.appendChild(l.cloneNode(true));
  });
  helmet.querySelectorAll('style').forEach((s) => {
    document.head.appendChild(s.cloneNode(true));
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', hoistHelmet);
} else {
  hoistHelmet();
}

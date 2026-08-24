export function mountStub(root, _ctx, title, message) {
  root.className = 'view view-stub';
  root.innerHTML = `
    <div class="stub-screen">
      <h2>${title}</h2>
      <p>${message}</p>
      <p style="font-size:var(--sam-fs-sm);color:var(--sam-text-muted)">Static demo &middot; full build later</p>
    </div>`;
  return () => {};
}

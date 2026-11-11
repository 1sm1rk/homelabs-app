// History-API routing. The first path segment names what fills the outlet:
//   /                → start (first permitted micro frontend, or the empty state)
//   /design/**       → the shell's living design guide
//   /<mfe-id>/**     → that micro frontend; the remainder is its own path
// Clicks on same-origin links the shell owns — in the shell or inside a micro
// frontend's shadow root — become pushState navigations instead of page loads.

const NOT_OURS = /^\/(bff|api|mfe|shell|oauth2|login|logout|health|error)(\/|$)/;
const listeners = new Set();

export function current() {
  const { pathname, search, hash } = window.location;
  const segments = pathname.split('/').filter(Boolean);
  if (segments.length === 0) return { kind: 'home' };
  const rest = `/${segments.slice(1).join('/')}${search}${hash}`;
  if (segments[0] === 'design') return { kind: 'design', rest };
  return { kind: 'mfe', id: segments[0], rest };
}

export function navigate(url, { replace = false } = {}) {
  const target = new URL(url, window.location.origin);
  const same = target.pathname + target.search + target.hash
      === window.location.pathname + window.location.search + window.location.hash;
  if (same && !replace) return;
  window.history[replace ? 'replaceState' : 'pushState']({}, '', target);
  notify();
}

export function onRoute(listener) {
  listeners.add(listener);
}

function notify() {
  listeners.forEach((listener) => listener(current()));
}

export function start() {
  window.addEventListener('popstate', notify);
  document.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const anchor = event.composedPath().find((node) => node instanceof HTMLAnchorElement);
    if (!anchor || anchor.target || anchor.hasAttribute('download')) return;
    const url = new URL(anchor.href, window.location.href);
    if (url.origin !== window.location.origin || NOT_OURS.test(url.pathname)) return;
    event.preventDefault();
    navigate(url.pathname + url.search + url.hash);
  });
}

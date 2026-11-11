// Loading and mounting micro frontends.
//
// Contract (the ONLY interface between shell and MFE): the bundle at `entry`
// defines the custom element `element`; the shell creates it in the outlet and
// sets the property `context` — a frozen object, replaced (never mutated) on
// every route or locale change:
//   id, basePath, path, locale, api, styles, navigate(path), href(path), setTrail(items)
// `styles` are constructed stylesheets (ui.css) to adopt into the shadow root;
// tokens arrive by inheritance.

const loading = new Map();
let sharedStyles = null;

/** ui.css as one constructed stylesheet, shared by every shadow root. */
export async function styles() {
  if (!sharedStyles) {
    sharedStyles = fetch('/shell/ui.css', { credentials: 'same-origin' })
      .then((res) => {
        if (!res.ok) throw new Error(`ui.css: HTTP ${res.status}`);
        return res.text();
      })
      .then((css) => {
        const sheet = new CSSStyleSheet();
        sheet.replaceSync(css);
        return Object.freeze([sheet]);
      });
  }
  return sharedStyles;
}

/** Imports the bundle once and waits until it has defined its element. */
export async function load(mfe) {
  if (!loading.has(mfe.entry)) {
    loading.set(mfe.entry, import(mfe.entry).then(() => customElements.whenDefined(mfe.element)));
  }
  try {
    await loading.get(mfe.entry);
  } catch (error) {
    loading.delete(mfe.entry); // a later retry imports again
    throw error;
  }
}

/** The mounted element for this MFE — reused while the same MFE stays open. */
export function mount(outlet, mfe) {
  const present = outlet.firstElementChild;
  if (present && present.localName === mfe.element) return present;
  const element = document.createElement(mfe.element);
  element.dataset.mfe = mfe.id;
  outlet.replaceChildren(element);
  return element;
}

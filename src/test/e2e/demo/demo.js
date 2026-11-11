// Demo micro frontend — TEST ONLY, never shipped. It proves the shell contract:
// it defines <hl-demo>, renders into its own shadow root with the shared
// styles, reads data only through context.api, sets the breadcrumb and
// navigates through the context.

const TEXT = {
  de: { title: 'Demo', items: 'Einträge', status: 'Status', owner: 'Zugewiesen', due: 'Fällig', back: 'Zurück', overview: 'Übersicht', comments: 'Kommentare', activity: 'Aktivität', add: 'Neuer Eintrag', edit: 'Bearbeiten', save: 'Speichern', cancel: 'Abbrechen' },
  en: { title: 'Demo', items: 'Items', status: 'Status', owner: 'Assignee', due: 'Due', back: 'Back', overview: 'Overview', comments: 'Comments', activity: 'Activity', add: 'New item', edit: 'Edit', save: 'Save', cancel: 'Cancel' },
};

const PILL = { todo: 'pill', in_progress: 'pill pill--progress', blocked: 'pill pill--warning', done: 'pill pill--success' };
const STATUS = { de: { todo: 'Offen', in_progress: 'In Arbeit', blocked: 'Blockiert', done: 'Erledigt' }, en: { todo: 'Open', in_progress: 'In progress', blocked: 'Blocked', done: 'Done' } };

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (value !== null && value !== undefined) node.setAttribute(key, value);
  }
  for (const child of children.flat()) if (child !== null && child !== undefined) node.append(child);
  return node;
}

class DemoMfe extends HTMLElement {
  #context = null;
  #items = null;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  set context(context) {
    this.#context = context;
    this.shadowRoot.adoptedStyleSheets = [...context.styles];
    this.#render();
  }

  get context() {
    return this.#context;
  }

  async #render() {
    const ctx = this.#context;
    const text = TEXT[ctx.locale] ?? TEXT.de;
    this.#items ??= await ctx.api.get('/items');
    const ref = ctx.path.split('/').filter(Boolean)[0];
    if (ref) {
      this.#detail(this.#items.find((item) => item.ref === ref), text);
    } else {
      this.#list(text);
    }
  }

  #list(text) {
    const ctx = this.#context;
    ctx.setTrail([]);
    const rows = this.#items.map((item) => el('tr', { class: 'is-clickable', onclick: () => ctx.navigate(`/${item.ref}`) },
      el('td', {}, el('span', { class: 'ref' }, item.ref), ' ', el('a', { href: ctx.href(`/${item.ref}`), class: 'cell-title' }, item.title)),
      el('td', {}, el('span', { class: PILL[item.status] }, STATUS[ctx.locale][item.status])),
      el('td', {}, item.owner ? el('span', { class: 'person' }, el('span', { class: 'avatar' }, item.owner.slice(0, 2)), item.owner) : el('span', { class: 'subtle' }, '—')),
      el('td', { class: 'cell-num subtle' }, item.due ?? '—')));
    this.shadowRoot.replaceChildren(el('div', { class: 'page' },
      el('header', { class: 'page-header' },
        el('div', { class: 'page-header__main' }, el('h1', { class: 'page-title' }, text.items),
          el('div', { class: 'page-meta' }, `${this.#items.length} ${text.items}`)),
        el('div', { class: 'page-header__actions' }, el('button', { class: 'btn btn--primary', type: 'button' }, text.add))),
      el('div', { class: 'card' }, el('table', { class: 'table' },
        el('thead', {}, el('tr', {}, el('th', {}, text.title), el('th', {}, text.status), el('th', {}, text.owner), el('th', { class: 'cell-num' }, text.due))),
        el('tbody', {}, rows)))));
  }

  #detail(item, text) {
    const ctx = this.#context;
    ctx.setTrail([{ label: item.ref }]);
    this.shadowRoot.replaceChildren(el('div', { class: 'page' },
      el('header', { class: 'page-header' },
        el('div', { class: 'page-header__main' },
          el('div', { class: 'page-meta' }, el('span', { class: 'ref' }, item.ref), el('span', { class: PILL[item.status] }, STATUS[ctx.locale][item.status])),
          el('h1', { class: 'page-title' }, item.title)),
        el('div', { class: 'page-header__actions' },
          el('button', { class: 'btn', type: 'button', onclick: () => ctx.navigate('/') }, text.back),
          el('button', { class: 'btn btn--primary', type: 'button' }, text.edit))),
      el('div', { class: 'tabs', role: 'tablist' },
        el('button', { class: 'tab', role: 'tab', 'aria-selected': 'true', type: 'button' }, text.overview),
        el('button', { class: 'tab', role: 'tab', 'aria-selected': 'false', type: 'button' }, text.comments),
        el('button', { class: 'tab', role: 'tab', 'aria-selected': 'false', type: 'button' }, text.activity)),
      el('dl', { class: 'record' },
        el('dt', {}, text.status), el('dd', {}, el('span', { class: PILL[item.status] }, STATUS[ctx.locale][item.status])),
        el('dt', {}, text.owner), el('dd', {}, item.owner ?? '—'),
        el('dt', {}, text.due), el('dd', {}, item.due ?? '—'))));
  }
}

customElements.define('hl-demo', DemoMfe);

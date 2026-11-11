// The living design guide (/design): every token and every ui.css component,
// rendered exactly the way a micro frontend renders — inside a shadow root that
// adopts the shared stylesheet. New patterns land here first; MFEs copy them.

import { h, icon, ICON_NAMES, initials } from './dom.js';

const GUIDE_CSS = `
  .guide-section { margin-top: var(--space-10); }
  .guide-section:first-of-type { margin-top: 0; }
  .guide-section > .section-title { font-size: var(--text-lg); margin-bottom: var(--space-1); }
  .guide-lead { color: var(--color-text-muted); margin-bottom: var(--space-4); max-width: 72ch; }
  .specimen { padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); }
  .specimen + .specimen { margin-top: var(--space-3); }
  .specimen__row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
  .specimen__classes { margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px dashed var(--color-border); font-family: var(--font-mono); font-size: var(--text-xs); color: var(--color-text-subtle); }
  .swatches { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-3); }
  .swatch { border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; background: var(--color-surface); }
  .swatch__chip { height: 44px; border-bottom: 1px solid var(--color-border); }
  .swatch__name { padding: var(--space-2); font-family: var(--font-mono); font-size: 11px; color: var(--color-text-muted); }
  .type-row { display: grid; grid-template-columns: 120px 1fr; align-items: baseline; gap: var(--space-4); padding: var(--space-2) 0; border-bottom: 1px solid var(--color-border); }
  .type-row:last-child { border-bottom: 0; }
  .space-row { display: flex; align-items: center; gap: var(--space-3); padding: 2px 0; font-family: var(--font-mono); font-size: var(--text-xs); color: var(--color-text-muted); }
  .space-bar { height: 10px; border-radius: 2px; background: var(--color-accent); opacity: 0.7; }
  .icon-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: var(--space-2); }
  .icon-cell { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2); border-radius: var(--radius-md); font-family: var(--font-mono); font-size: 11px; color: var(--color-text-muted); }
  .icon-cell .icon { color: var(--color-text); }
  .static-menu { position: static; justify-self: start; align-self: start; box-shadow: var(--shadow-md); }
  .grid-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: var(--space-4); }
`;

let guideSheet = null;

function sheet() {
  if (!guideSheet) {
    guideSheet = new CSSStyleSheet();
    guideSheet.replaceSync(GUIDE_CSS);
  }
  return guideSheet;
}

const section = (title, lead, ...content) => h('section', { class: 'guide-section' },
  h('h2', { class: 'section-title' }, title), h('p', { class: 'guide-lead' }, lead), ...content);

const specimen = (classes, ...content) => h('div', { class: 'specimen' },
  ...content, h('div', { class: 'specimen__classes' }, classes));

const row = (...content) => h('div', { class: 'specimen__row' }, ...content);

function colors() {
  const names = ['--color-bg', '--color-bg-subtle', '--color-surface', '--color-hover', '--color-selected',
    '--color-border', '--color-border-strong', '--color-text', '--color-text-muted', '--color-text-subtle',
    '--color-accent', '--color-accent-soft', '--status-neutral', '--status-info', '--status-progress',
    '--status-success', '--status-warning', '--status-danger'];
  return h('div', { class: 'swatches' }, names.map((name) => {
    const chip = h('div', { class: 'swatch__chip' });
    chip.style.background = `var(${name})`;
    return h('div', { class: 'swatch' }, chip, h('div', { class: 'swatch__name' }, name));
  }));
}

function typeScale() {
  const steps = [['--text-2xl', 'Page heading 28'], ['--text-xl', 'Page title 20'], ['--text-lg', 'Panel title 16'],
    ['--text-md', 'Body 14 — the default'], ['--text-sm', 'Secondary 13'], ['--text-xs', 'Meta 12']];
  return h('div', {}, steps.map(([token, sample]) => {
    const text = h('span', {}, sample);
    text.style.fontSize = `var(${token})`;
    return h('div', { class: 'type-row' }, h('span', { class: 'mono subtle' }, token), text);
  }));
}

function spacing() {
  return h('div', {}, [1, 2, 3, 4, 5, 6, 8, 10].map((step) => {
    const bar = h('span', { class: 'space-bar' });
    bar.style.width = `var(--space-${step})`;
    return h('div', { class: 'space-row' }, h('span', {}, `--space-${step}`), bar);
  }));
}

function pills() {
  return row(
    h('span', { class: 'pill' }, 'Offen'),
    h('span', { class: 'pill pill--progress' }, 'In Arbeit'),
    h('span', { class: 'pill pill--warning' }, 'Blockiert'),
    h('span', { class: 'pill pill--success' }, 'Erledigt'),
    h('span', { class: 'pill pill--info' }, 'Aktiv'),
    h('span', { class: 'pill pill--danger' }, 'Fehler'),
    h('span', { class: 'pill pill--muted' }, 'Archiviert'));
}

function table() {
  const toggle = (expanded) => h('button', { class: 'tree-toggle', type: 'button', 'aria-expanded': String(expanded), 'aria-label': 'Toggle' }, icon('chevron-right'));
  const person = (name) => h('span', { class: 'person' }, h('span', { class: 'avatar' }, initials(name)), name);
  return h('div', { class: 'card' }, h('table', { class: 'table' },
    h('thead', {}, h('tr', {}, h('th', {}, 'Titel'), h('th', {}, 'Status'), h('th', {}, 'Zugewiesen'), h('th', { class: 'cell-num' }, 'Fällig'))),
    h('tbody', {},
      h('tr', { class: 'tree-parent is-clickable' },
        h('td', {}, toggle(true), h('span', { class: 'ref' }, 'HA-2'), ' ', h('span', { class: 'cell-title' }, 'Shell + Design-System')),
        h('td', {}, h('span', { class: 'pill pill--progress' }, 'In Arbeit')), h('td', {}, person('di.mueller')), h('td', { class: 'cell-num subtle' }, '14.11.')),
      h('tr', { class: 'tree-child is-clickable', 'aria-selected': 'true' },
        h('td', {}, h('span', { class: 'ref' }, 'HA-7'), ' Design-Katalog'),
        h('td', {}, h('span', { class: 'pill pill--success' }, 'Erledigt')), h('td', {}, person('di.mueller')), h('td', { class: 'cell-num subtle' }, '—')),
      h('tr', { class: 'tree-child is-clickable' },
        h('td', {}, h('span', { class: 'ref' }, 'HA-8'), ' Rail einklappbar'),
        h('td', {}, h('span', { class: 'pill' }, 'Offen')), h('td', { class: 'subtle' }, '—'), h('td', { class: 'cell-num subtle' }, '—')),
      h('tr', { class: 'tree-parent is-clickable' },
        h('td', {}, toggle(false), h('span', { class: 'ref' }, 'HA-3'), ' ', h('span', { class: 'cell-title' }, 'Registry + Gate + Proxy')),
        h('td', {}, h('span', { class: 'pill pill--warning' }, 'Blockiert')), h('td', { class: 'subtle' }, '—'), h('td', { class: 'cell-num subtle' }, '20.11.')))));
}

function forms() {
  const field = (labelText, control, hint, error) => h('label', { class: 'field' },
    h('span', { class: 'field__label' }, labelText), control,
    hint ? h('span', { class: 'field__hint' }, hint) : null,
    h('span', { class: 'field__error' }, error ?? ''));
  return h('div', { class: 'form-grid' },
    field('Titel', h('input', { class: 'input', type: 'text', value: 'Shell + Design-System' })),
    field('Status', h('select', { class: 'select' }, h('option', {}, 'In Arbeit'), h('option', {}, 'Offen'))),
    field('Fällig', h('input', { class: 'input', type: 'date', value: '2026-11-14' })),
    field('Position', h('input', { class: 'input', type: 'number', min: '1', value: '2' }), '1 = zuerst'),
    field('Kurzcode', h('input', { class: 'input', type: 'text', value: 'HP', 'aria-invalid': 'true' }), null,
      'short_code: bereits vergeben — frei: HP2, HPR'),
    h('label', { class: 'field field--wide' }, h('span', { class: 'field__label' }, 'Beschreibung'),
      h('textarea', { class: 'textarea', placeholder: 'Was ist das Ziel?' })));
}

function menu() {
  return h('div', { class: 'menu static-menu', role: 'menu' },
    h('button', { class: 'menu__item', type: 'button' }, icon('edit'), 'Bearbeiten'),
    h('button', { class: 'menu__item', type: 'button' }, icon('archive'), 'Deaktivieren'),
    h('div', { class: 'menu__separator' }),
    h('button', { class: 'menu__item menu__item--danger', type: 'button' }, icon('archive'), 'Archivieren …'));
}

function panels(root) {
  const drawer = h('dialog', { class: 'drawer', 'aria-label': 'Drawer' },
    h('div', { class: 'panel__header' }, h('h2', { class: 'panel__title' }, 'HA-7 bearbeiten'),
      h('button', { class: 'btn btn--ghost btn--icon', type: 'button', 'aria-label': 'Schließen', onclick: () => drawer.close() }, icon('close'))),
    h('div', { class: 'panel__body' }, forms()),
    h('div', { class: 'panel__footer' },
      h('button', { class: 'btn', type: 'button', onclick: () => drawer.close() }, 'Abbrechen'),
      h('button', { class: 'btn btn--primary', type: 'button', onclick: () => drawer.close() }, 'Speichern')));
  const dialog = h('dialog', { class: 'dialog', 'aria-label': 'Dialog' },
    h('div', { class: 'panel__header' }, h('h2', { class: 'panel__title' }, 'Projekt archivieren?')),
    h('div', { class: 'panel__body' }, h('p', { class: 'muted' }, 'Archivierte Projekte sind schreibgeschützt. Das lässt sich nicht rückgängig machen.')),
    h('div', { class: 'panel__footer' },
      h('button', { class: 'btn', type: 'button', onclick: () => dialog.close() }, 'Abbrechen'),
      h('button', { class: 'btn btn--danger', type: 'button', onclick: () => dialog.close() }, 'Archivieren')));
  root.append(drawer, dialog);
  return row(
    h('button', { class: 'btn', type: 'button', onclick: () => drawer.showModal() }, icon('edit'), 'Drawer öffnen'),
    h('button', { class: 'btn', type: 'button', onclick: () => dialog.showModal() }, icon('alert'), 'Dialog öffnen'));
}

export function render(outlet, { styles, t }) {
  const host = h('div', { class: 'design-guide' });
  const root = host.attachShadow({ mode: 'open' });
  root.adoptedStyleSheets = [...styles, sheet()];

  root.append(h('div', { class: 'page' },
    h('header', { class: 'page-header' },
      h('div', { class: 'page-header__main' },
        h('h1', { class: 'page-title' }, t('design.title')),
        h('div', { class: 'page-meta' }, 'tokens.css · ui.css — the contract every micro frontend styles itself with'))),

    section('Colour', 'Semantic tokens only — components never use raw palette values. Status colours mark status and nothing else.',
      specimen('var(--color-*) · var(--status-*)', colors())),
    section('Type', 'One system stack, 14 px body. Hierarchy comes from size and weight, never from colour.',
      specimen('var(--text-xs … --text-2xl) · .page-title · .section-title · .muted · .subtle · .mono · .ref', typeScale())),
    section('Spacing', '4 px rhythm. Controls and rows share one height (--control-height, --row-height = 32 px).',
      specimen('var(--space-1 … --space-10)', spacing())),
    section('Buttons', 'One primary action per view. Ghost for secondary tools, danger only inside a confirmation.',
      specimen('.btn · .btn--primary · .btn--ghost · .btn--danger · .btn--icon · .btn--sm',
        row(h('button', { class: 'btn btn--primary', type: 'button' }, icon('plus'), 'Neues Projekt'),
          h('button', { class: 'btn', type: 'button' }, 'Abbrechen'),
          h('button', { class: 'btn btn--ghost', type: 'button' }, icon('filter'), 'Filter'),
          h('button', { class: 'btn btn--danger', type: 'button' }, 'Archivieren'),
          h('button', { class: 'btn btn--ghost btn--icon', type: 'button', 'aria-label': 'Mehr' }, icon('more')),
          h('button', { class: 'btn btn--sm', type: 'button' }, 'Klein'),
          h('button', { class: 'btn', type: 'button', disabled: true }, 'Deaktiviert')))),
    section('Status and labels', 'A status is a pill with a dot. Labels are quiet outlines; people show with initials.',
      specimen('.pill · .pill--progress|warning|success|info|danger|muted · .label · .labels · .avatar · .person', pills(),
        h('div', { class: 'specimen__row' }, h('span', { class: 'labels' }, h('span', { class: 'label' }, 'frontend'), h('span', { class: 'label' }, 'dogfood')),
          h('span', { class: 'person' }, h('span', { class: 'avatar' }, 'DM'), 'di.mueller')))),
    section('Table and tree', 'Rows read; a click opens the record or its drawer. Parents fold. References are mono and quiet.',
      specimen('.card · .table · .tree-parent · .tree-child · .tree-toggle · .is-clickable · .cell-title · .cell-num · .ref', table())),
    section('Tabs', 'Local navigation inside a micro frontend — there is no shell subnav.',
      specimen('.tabs · .tab[aria-selected] · .tab__count',
        h('div', { class: 'tabs', role: 'tablist' },
          h('button', { class: 'tab', role: 'tab', 'aria-selected': 'true', type: 'button' }, 'Plan'),
          h('button', { class: 'tab', role: 'tab', 'aria-selected': 'false', type: 'button' }, 'Kommentare', h('span', { class: 'tab__count' }, '3')),
          h('button', { class: 'tab', role: 'tab', 'aria-selected': 'false', type: 'button' }, 'Aktivität'),
          h('button', { class: 'tab', role: 'tab', 'aria-selected': 'false', type: 'button' }, 'Anhänge')))),
    section('Forms', 'Fields stack label, control, hint and error. Errors quote the service verbatim, with the wire field name.',
      specimen('.form-grid · .field · .field__label · .field__hint · .field__error · .input · .select · .textarea · [aria-invalid]', forms(),
        h('div', { class: 'specimen__row' }, h('div', { class: 'search' }, icon('search'), h('input', { class: 'input', type: 'search', placeholder: 'Suchen …' }))))),
    section('Menu, drawer, dialog', 'Destructive and rare actions live in the ⋯ menu. Editing opens a drawer; irreversible steps ask in a dialog.',
      specimen('.menu-anchor · .menu · .menu__item · .menu__item--danger · dialog.drawer · dialog.dialog · .panel__header|body|footer',
        h('div', { class: 'grid-2' }, menu(), panels(root)))),
    section('Record and feed', 'A record is a definition list; comments and activity are a feed, newest first.',
      h('div', { class: 'grid-2' },
        specimen('.record',
          h('dl', { class: 'record' },
            h('dt', {}, 'Status'), h('dd', {}, h('span', { class: 'pill pill--progress' }, 'In Arbeit')),
            h('dt', {}, 'Zugewiesen'), h('dd', {}, h('span', { class: 'person' }, h('span', { class: 'avatar' }, 'DM'), 'di.mueller')),
            h('dt', {}, 'Fällig'), h('dd', {}, '14.11.2026'),
            h('dt', {}, 'Labels'), h('dd', {}, h('span', { class: 'labels' }, h('span', { class: 'label' }, 'design'))))),
        specimen('.feed · .feed__item · .feed__meta · .feed__author · .feed__time · .feed__text',
          h('ul', { class: 'feed' },
            h('li', { class: 'feed__item' }, h('span', { class: 'avatar' }, 'DM'), h('div', {},
              h('div', { class: 'feed__meta' }, h('span', { class: 'feed__author' }, 'di.mueller'), h('span', { class: 'feed__time' }, 'vor 2 Std.')),
              h('p', { class: 'feed__text' }, 'Ruhig und dicht, bitte — die Grundstruktur bleibt.'))),
            h('li', { class: 'feed__item' }, h('span', { class: 'avatar' }, 'DM'), h('div', {},
              h('div', { class: 'feed__meta' }, h('span', { class: 'feed__author' }, 'di.mueller'), h('span', { class: 'feed__time' }, 'gestern')),
              h('p', { class: 'feed__text' }, 'Status von Offen auf In Arbeit gesetzt.'))))))),
    section('States', 'Empty, failed and loading are states of their own — never a blank area.',
      h('div', { class: 'grid-2' },
        specimen('.empty · .empty__title', h('div', { class: 'empty' }, h('p', { class: 'empty__title' }, 'Noch keine Kommentare'), h('p', {}, 'Schreib den ersten.'))),
        specimen('.notice · .notice--danger · .notice__detail · .skeleton',
          h('div', { class: 'notice notice--danger', role: 'alert' }, icon('alert'),
            h('div', {}, 'Der Dienst hat die Änderung abgelehnt.', h('div', { class: 'notice__detail' }, 'status: archived projects are read-only'))),
          h('div', {}, h('span', { class: 'skeleton' })), h('div', {}, h('span', { class: 'skeleton' }))))),
    section('Icons', 'Descriptors may name any of these; unknown names fall back to "app".',
      specimen('<svg class="icon"><use href="/shell/icons.svg#name"></use></svg>',
        h('div', { class: 'icon-grid' }, [...ICON_NAMES].map((name) => h('div', { class: 'icon-cell' }, icon(name), name)))))));

  outlet.replaceChildren(host);
}

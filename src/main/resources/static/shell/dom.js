// Small DOM helpers. Elements are built with createElement + textContent only —
// never innerHTML with data — so nothing a service or a person typed can turn
// into markup.

const SVG = 'http://www.w3.org/2000/svg';
const ICONS = '/shell/icons.svg';

/** Known icon ids of the sprite; descriptors naming anything else get "app". */
export const ICON_NAMES = new Set([
  'logo', 'app', 'folder', 'brain', 'list', 'palette', 'sidebar', 'chevron-down', 'chevron-right',
  'more', 'plus', 'close', 'search', 'filter', 'edit', 'archive', 'comment', 'activity', 'paperclip',
  'calendar', 'user', 'log-out', 'sun', 'moon', 'monitor', 'alert', 'lock', 'inbox', 'check',
]);

/**
 * h('a', {class: 'x', href: '/'}, 'text', child) — attributes as strings; `on*`
 * functions become listeners; null/false attributes and children are skipped.
 */
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs ?? {})) {
    if (value === null || value === undefined || value === false) continue;
    if (name.startsWith('on') && typeof value === 'function') {
      el.addEventListener(name.slice(2).toLowerCase(), value);
    } else {
      el.setAttribute(name, value === true ? '' : String(value));
    }
  }
  append(el, children);
  return el;
}

export function append(parent, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return parent;
}

export function icon(name, extraClass = '') {
  const id = ICON_NAMES.has(name) ? name : 'app';
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('class', `icon ${extraClass}`.trim());
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG, 'use');
  use.setAttribute('href', `${ICONS}#${id}`);
  svg.append(use);
  return svg;
}

export function initials(name) {
  const parts = String(name ?? '').split(/[\s._-]+/).filter(Boolean);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : (parts[0] ?? '?').slice(0, 2)).toUpperCase();
}

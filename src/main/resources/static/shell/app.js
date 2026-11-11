// Shell boot: who is signed in, which micro frontends they may use, then route.

import { bff, createApi } from './api.js';
import { h, icon, initials } from './dom.js';
import { initialLocale, locale, setLocale, t, translate } from './i18n.js';
import { load, mount, styles } from './mount.js';
import * as router from './router.js';

const outlet = document.getElementById('outlet');
const app = document.getElementById('app');
const state = { me: null, mfes: [], styles: [], trail: [] };

// ---------- Preferences (per browser; storage may be unavailable) ---------------

function stored(key, fallback) {
  try { return window.localStorage.getItem(key) ?? fallback; } catch { return fallback; }
}

function store(key, value) {
  try { window.localStorage.setItem(key, value); } catch { /* preference only */ }
}

function applyTheme(choice) {
  if (choice === 'light' || choice === 'dark') {
    document.documentElement.dataset.theme = choice;
  } else {
    delete document.documentElement.dataset.theme;
  }
  document.querySelectorAll('[data-theme-choice]').forEach((item) => {
    item.setAttribute('aria-checked', String(item.dataset.themeChoice === (choice ?? 'system')));
  });
}

function applyRail(mode) {
  app.dataset.rail = mode;
  const toggle = document.getElementById('rail-toggle');
  toggle.setAttribute('aria-expanded', String(mode !== 'collapsed'));
  toggle.querySelector('[data-i18n]').dataset.i18n = mode === 'collapsed' ? 'rail.expand' : 'rail.collapse';
  translate(toggle);
}

// ---------- Chrome ---------------------------------------------------------------

function label(mfe) {
  return mfe.nav?.label?.[locale()] ?? mfe.nav?.label?.de ?? mfe.id;
}

function renderRail() {
  const list = document.getElementById('rail-list');
  list.replaceChildren(...state.mfes.map((mfe) => h('li', {},
    h('a', { class: 'rail__link', href: `/${mfe.id}`, 'data-route': mfe.id, title: label(mfe) },
      icon(mfe.nav?.icon), h('span', { class: 'rail__label' }, label(mfe))))));
}

function markActive(route) {
  const active = route.kind === 'mfe' ? route.id : route.kind;
  document.querySelectorAll('.rail__link[data-route]').forEach((link) => {
    if (link.dataset.route === active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
}

/** items: [{label, href?}] — the last item is the current page. */
function renderTrail(items) {
  state.trail = items;
  const list = document.getElementById('trail');
  list.replaceChildren(...items.map((item, index) => h('li', { class: 'trail__item' },
    item.href && index < items.length - 1
      ? h('a', { class: 'trail__link', href: item.href }, item.label)
      : h('span', { 'aria-current': index === items.length - 1 ? 'page' : null }, item.label))));
  document.title = items.length ? `${items[items.length - 1].label} · homelabs` : 'homelabs';
}

function renderPerson() {
  document.getElementById('person-name').textContent = state.me.name;
  document.getElementById('person-avatar').textContent = initials(state.me.name);
}

function renderLocaleSwitch() {
  document.querySelectorAll('[data-locale]').forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset.locale === locale()));
  });
}

function showState(kind, { detail = null, retry = null } = {}) {
  const icons = { noApps: 'inbox', noAccess: 'lock', notFound: 'search', loadError: 'alert' };
  outlet.replaceChildren(h('section', { class: `state ${kind === 'loadError' ? 'state--error' : ''}`, role: 'status' },
    h('div', { class: 'state__box' },
      h('div', { class: 'state__icon' }, icon(icons[kind])),
      h('h1', { class: 'state__title' }, t(`state.${kind}.title`)),
      h('p', { class: 'state__text' }, t(`state.${kind}.text`)),
      detail ? h('p', { class: 'state__detail' }, detail) : null,
      retry ? h('p', {}, h('button', { class: 'state__action', type: 'button', onclick: retry }, t('state.loadError.retry'))) : null)));
}

// ---------- Routing --------------------------------------------------------------

function contextFor(mfe, route) {
  const basePath = `/${mfe.id}`;
  const resolve = (path = '/') => basePath + (path.startsWith('/') ? path : `/${path}`);
  return Object.freeze({
    id: mfe.id,
    basePath,
    path: route.rest,
    locale: locale(),
    api: createApi(mfe.id, () => showState('noAccess')),
    styles: state.styles,
    href: resolve,
    navigate: (path, options) => router.navigate(resolve(path), options),
    setTrail: (items = []) => renderTrail([
      { label: label(mfe), href: basePath },
      ...items.map((item) => ({ label: String(item.label), href: item.path ? resolve(item.path) : null })),
    ]),
  });
}

async function render(route = router.current()) {
  markActive(route);

  if (route.kind === 'home') {
    if (state.mfes.length > 0) {
      router.navigate(`/${state.mfes[0].id}`, { replace: true });
    } else {
      renderTrail([{ label: t('trail.home') }]);
      showState('noApps');
    }
    return;
  }

  if (route.kind === 'design') {
    renderTrail([{ label: t('design.title') }]);
    const design = await import('./design.js');
    design.render(outlet, { styles: state.styles, t, locale: locale() });
    return;
  }

  const mfe = state.mfes.find((candidate) => candidate.id === route.id);
  if (!mfe) {
    renderTrail([{ label: route.id }]);
    // Granted but not listed = the service did not answer; otherwise: no such app
    // (whether it exists for others is none of this person's business).
    if (state.me.mfes.includes(route.id)) showState('loadError', { retry: () => window.location.reload() });
    else showState('notFound');
    return;
  }

  const fresh = outlet.firstElementChild?.localName !== mfe.element;
  if (fresh) {
    renderTrail([{ label: label(mfe) }]);
    outlet.replaceChildren(h('div', { class: 'loading', role: 'progressbar' }));
  }
  try {
    await load(mfe);
  } catch (error) {
    showState('loadError', { detail: String(error?.message ?? error), retry: () => render() });
    return;
  }
  const element = mount(outlet, mfe);
  element.context = contextFor(mfe, route);
}

// ---------- Boot -----------------------------------------------------------------

function bindControls() {
  document.getElementById('rail-toggle').addEventListener('click', () => {
    const next = app.dataset.rail === 'collapsed' ? 'expanded' : 'collapsed';
    store('app.rail', next);
    applyRail(next);
  });

  document.querySelectorAll('[data-locale]').forEach((button) => button.addEventListener('click', async () => {
    await setLocale(button.dataset.locale);
    renderLocaleSwitch();
    renderRail();
    render();
  }));

  const personButton = document.getElementById('person-button');
  const menu = document.getElementById('person-menu');
  const closeMenu = () => { menu.hidden = true; personButton.setAttribute('aria-expanded', 'false'); };
  personButton.addEventListener('click', (event) => {
    event.stopPropagation();
    menu.hidden = !menu.hidden;
    personButton.setAttribute('aria-expanded', String(!menu.hidden));
  });
  document.addEventListener('click', (event) => { if (!menu.contains(event.target)) closeMenu(); });
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeMenu(); });

  document.querySelectorAll('[data-theme-choice]').forEach((item) => item.addEventListener('click', () => {
    store('app.theme', item.dataset.themeChoice);
    applyTheme(item.dataset.themeChoice);
  }));

  document.getElementById('sign-out').addEventListener('click', async () => {
    await bff.post('/logout').catch(() => null);
    window.location.assign('/');
  });
}

async function boot() {
  applyTheme(stored('app.theme', 'system'));
  applyRail(stored('app.rail', 'expanded'));
  await setLocale(initialLocale());
  renderLocaleSwitch();
  bindControls();

  const [me, mfes, sheets] = await Promise.all([
    bff.get('/me'),
    bff.get('/mfes').catch(() => []), // empty until the registry answers
    styles(),
  ]);
  state.me = me;
  state.mfes = [...(mfes ?? [])].sort((a, b) => (a.nav?.order ?? 0) - (b.nav?.order ?? 0));
  state.styles = sheets;

  renderPerson();
  renderRail();
  router.onRoute(render);
  router.start();
  render();
}

boot().catch((error) => {
  showState('loadError', { detail: String(error?.message ?? error), retry: () => window.location.reload() });
});

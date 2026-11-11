// Chrome texts of the shell, de (default) and en. The locale is chosen from the
// cookie app.locale, then the browser language, then de. Micro frontends get
// the locale through their context and translate their own UI.

export const LOCALES = ['de', 'en'];
const COOKIE = 'app.locale';

let current = 'de';
let messages = {};

export function initialLocale() {
  const fromCookie = document.cookie.split('; ').find((c) => c.startsWith(`${COOKIE}=`))?.split('=')[1];
  if (LOCALES.includes(fromCookie)) return fromCookie;
  const fromBrowser = (navigator.language ?? '').slice(0, 2).toLowerCase();
  return LOCALES.includes(fromBrowser) ? fromBrowser : 'de';
}

export async function setLocale(locale) {
  current = LOCALES.includes(locale) ? locale : 'de';
  const res = await fetch(`/shell/i18n/${current}.json`, { credentials: 'same-origin' });
  messages = res.ok ? await res.json() : {};
  document.documentElement.lang = current;
  document.cookie = `${COOKIE}=${current}; path=/; max-age=31536000; samesite=lax`;
  translate(document);
  return current;
}

export function locale() {
  return current;
}

export function t(key) {
  return messages[key] ?? key;
}

/** Applies data-i18n (text) and data-i18n-aria (aria-label) inside a root. */
export function translate(root) {
  root.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n); });
  root.querySelectorAll('[data-i18n-aria]').forEach((el) => { el.setAttribute('aria-label', t(el.dataset.i18nAria)); });
}

// Fetch wrappers. The browser holds no token: every call rides the session
// cookie, writes carry the CSRF token from the XSRF-TOKEN cookie as header, and
// the BFF relays the access token to the service.
//
// 401 → the session is gone: reload the page, the navigation goes through the
//       login and comes back here.
// 403 → stage-1 gate (or the service) refused: AccessDenied, the shell shows
//       its no-access state.

const WRITE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export class AccessDenied extends Error {
  constructor(path) {
    super(`access denied: ${path}`);
    this.name = 'AccessDenied';
  }
}

/** A non-2xx answer other than 401/403; `body` is the parsed problem body (or text). */
export class ApiError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export function csrfToken() {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null;
}

export function reLogin() {
  window.location.assign(window.location.href);
}

async function parse(res) {
  if (res.status === 204) return null;
  const type = res.headers.get('Content-Type') ?? '';
  if (type.includes('json')) return res.json();
  const text = await res.text();
  return text === '' ? null : text;
}

async function send(url, method, body, init = {}) {
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  let payload;
  if (body !== undefined) {
    if (body instanceof FormData || body instanceof Blob) {
      payload = body;
    } else {
      headers.set('Content-Type', 'application/json');
      payload = JSON.stringify(body);
    }
  }
  if (WRITE.has(method)) {
    const token = csrfToken();
    if (token) headers.set('X-XSRF-TOKEN', token);
  }
  const res = await fetch(url, { ...init, method, headers, body: payload, credentials: 'same-origin' });
  if (res.status === 401) {
    reLogin();
    throw new Error('session expired');
  }
  if (res.status === 403) throw new AccessDenied(url);
  if (!res.ok) throw new ApiError(res.status, await parse(res).catch(() => null));
  return parse(res);
}

/**
 * The API object a micro frontend gets as context.api: bound to /api/<id>, paths
 * relative to the service's own /api (e.g. api.get('/projects?status=active')).
 */
export function createApi(id, onDenied) {
  const base = `/api/${encodeURIComponent(id)}`;
  const guard = (promise) => promise.catch((error) => {
    if (error instanceof AccessDenied) onDenied?.();
    throw error;
  });
  return Object.freeze({
    base,
    url: (path) => base + path,
    get: (path, init) => guard(send(base + path, 'GET', undefined, init)),
    post: (path, body, init) => guard(send(base + path, 'POST', body, init)),
    put: (path, body, init) => guard(send(base + path, 'PUT', body, init)),
    patch: (path, body, init) => guard(send(base + path, 'PATCH', body, init)),
    delete: (path, init) => guard(send(base + path, 'DELETE', undefined, init)),
  });
}

/** The shell's own calls to the BFF (/bff/me, /bff/mfes, /bff/logout). */
export const bff = Object.freeze({
  get: (path) => send(`/bff${path}`, 'GET'),
  post: (path) => send(`/bff${path}`, 'POST'),
});

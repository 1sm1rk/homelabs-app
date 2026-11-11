# SRS — homelabs-app (Software Requirements Specification)

Minimal SRS. Work packages reference REQ-IDs here for traceability. Keep it
lean — extend only when a requirement has a real consumer.

## Scope

One web application: a backend for frontend (authentication, server-side
session, micro-frontend registry, access gate, same-origin proxy) and a plain
JavaScript shell that mounts micro frontends shipped by the backend services.
First micro frontend: projects (served by homelabs-projects).

## Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| REQ-1 | Authentication: OAuth2 Authorization Code + PKCE against the shared Keycloak realm (confidential client); server-side session in Postgres; `GET /health` open | navigations without session redirect to login; XHR without session gets 401; a session survives an application restart; logout deletes the session |
| REQ-2 | Shell: rail from the permitted micro frontends, topbar with breadcrumb, language switch (de/en) and person menu, working area; History-API routing `/<id>/**`; one `context` object per mounted micro frontend | micro frontends mount from their descriptor alone; load failures and missing access render as states, not blank pages |
| REQ-3 | Design system: design tokens + shared component stylesheet adopted by micro frontends; light/dark; living design guide at `/design` | every shared component appears in `/design`; micro frontends define no colors, sizes or fonts of their own |
| REQ-4 | Registry + gate: descriptors fetched from registered services and validated; access per Keycloak client role `mfe-<id>` on menu, bundle and API | without the role: no menu entry, 403 on bundle and API; invalid or unreachable descriptors drop only that entry |
| REQ-5 | Same-origin proxy: bundles and API calls proxied to the services; access token relayed on API calls; browser cookies and credentials never forwarded; CSRF on writes; strict CSP | Bearer present on API calls only; writes without CSRF header rejected; CSP header on every response |
| REQ-6 | Operability: `GET /health`; containerized image; pipeline publishing the image on main merges | `/health` 200 unauthenticated; main merge publishes the ghcr image |

## Constraints

- Java 25, Spring Boot 4.1.x, Maven, `de.homelabs.app`; records, virtual
  threads, constructor injection.
- Shell and micro frontends: native ES modules and Web Components, no build
  chain, no framework; no inline scripts or styles.
- Wire discipline: explicit `@JsonProperty` on every record crossing a REST
  boundary.

## Non-goals

- Fine-grained authorization in the BFF, back-channel logout, an admin UI
  for roles, micro-frontend-to-micro-frontend communication.

# homelabs-app

The micro-frontend app of the homelab services: a Spring Boot backend for
frontend (BFF) plus a plain-JavaScript shell, served as one application.
Each backend service ships its own micro frontend — a Web Component in an ES
module — and describes it through `GET /mfe/descriptor`. The BFF collects
those descriptions into the navigation, serves the bundles and API calls from
one origin, and mounts each micro frontend into the shell at runtime.

Authentication rides the shared Keycloak realm (OAuth2 Authorization Code +
PKCE). The BFF keeps the tokens in a server-side session (Postgres); the
browser only holds a session cookie. Access to each micro frontend is granted
per Keycloak client role (`mfe-<id>`); the backends keep their own record-level
rules.

No frontend build chain, no JavaScript or CSS framework.

## How to run / verify

- Dev database: `docker compose -f compose.dev.yml up -d` (Postgres 17 on
  localhost:5432, database `app`; Flyway creates the session tables).
- Run: `mvn spring-boot:run` (dev port 8095). Needs `APP_CLIENT_SECRET` (the
  realm client's secret); micro frontends are registered as
  `app.mfes.<id>.base-url` (`APP_MFE_PROJECTS_URL`, default
  `http://localhost:8093`).
- Verify (tests): `mvn clean verify` — the integration tests start a Postgres
  test container, so Docker must be available.
- `GET /health` is the open liveness probe.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`): pull requests run `verify`; a
merge to `main` runs `verify` and publishes the container image
`ghcr.io/1sm1rk/homelabs-app` (`latest` and `sha-<commit>`). The image is a
multi-stage build (`Dockerfile`, non-root, port 8095).

## Commit messages

Lean, describe the implementation only — no references to planning artifacts.

## License

Apache-2.0 (see [LICENSE](LICENSE)).

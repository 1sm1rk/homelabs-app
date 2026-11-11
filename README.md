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

Activates with the first implementation step (the repository currently holds
the scaffold):

- Run: `mvn spring-boot:run` (dev port 8095)
- Verify (tests): `mvn clean verify`

`GET /health` is the open liveness probe once the application boots.

## Commit messages

Lean, describe the implementation only — no references to planning artifacts.

## License

Apache-2.0 (see [LICENSE](LICENSE)).

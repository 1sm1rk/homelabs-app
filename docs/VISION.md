# VISION — homelabs-app

## Why this project exists

The homelab's services grow independently, and so should their user
interfaces. This project composes the homelab's browser surface from micro
frontends: every service ships the UI for its own domain, and one shell —
backed by a backend for frontend — brings them together under one login, one
origin and one design. The shell and the micro frontends stay plain
(ES modules, Web Components, hand-written CSS), so every piece remains small,
repairable and deployable on its own.

## Users

Several people in the homelab with different rights: the owner as admin with
access to everything, other people with access to the applications released to
them. Rights come from the shared Keycloak realm.

## Non-goals

- No JavaScript framework, no npm/node build, no CSS framework.
- No tokens in the browser — the BFF holds them.
- No domain data in the app: the backends own their records and their
  record-level rules.
- No fine-grained permission model in the BFF — access is granted per micro
  frontend.

## Success criteria

- Daily use: project work happens in the app instead of around the API.
- A new micro frontend joins with a descriptor and a bundle in its service,
  one registration in the BFF and one Keycloak role — no shell surgery.
- People see exactly the applications their role allows — in the menu, in
  the bundles and in the API.

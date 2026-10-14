Closes #

## What changed

## How to test

## Checklist
- [ ] CI passes
- [ ] New behavior has tests (business rules: one test per BR-xx touched)
- [ ] Migrations run on a clean database (if any)
- [ ] Docs updated where behavior, API or decisions changed (requirements, ADR, OpenAPI)
- [ ] UI matches `docs/design/telas` and uses design-system tokens (if frontend)
- [ ] Nothing outside the issue's scope

## Security
- [ ] No secrets in code, config, logs or the frontend bundle (new variables added to `.env.example` without values)
- [ ] New endpoints are authenticated by default and have authorization tests (anonymous and other user)
- [ ] Inputs validated (types, sizes); no string-built SQL; no user-provided URLs fetched
- [ ] No raw HTML from users or providers rendered
- [ ] New dependencies listed below and pinned
- [ ] Controls in `docs/security.md` for the touched area still hold

New dependencies:

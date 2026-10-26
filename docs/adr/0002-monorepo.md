# ADR 0002: One repository for the API and the Android app

- **Status:** Accepted
- **Date:** 30 September 2026

## Context

The project has two deliverables: a Spring Boot API and an Android client that calls it. We started
with separate repositories, but the app and the API change together: a new endpoint needs a DTO,
a repository method, and a screen in the app, and the OpenAPI contract describes both sides. With two
repositories, one feature meant two pull requests that had to be reviewed and merged in step.

## Decision

Keep both projects in **one repository**, with independent builds:

- `backend/`: the API's Gradle build, Dockerfile, detekt ruleset, and tests.
- `frontend/`: the Android app's Gradle build, detekt ruleset, and tests.
- `docs/`: the shared OpenAPI contract and design documents.
- `.github/workflows/`: `backend.yml` and `frontend.yml`, one workflow per project.

## Consequences

- **Good:** a feature that touches the API, the contract, and the app ships in one pull request.
- **Good:** one README, one issue tracker, and one project board for the team.
- **Good:** `docs/openapi.yaml` sits next to both implementations, which makes drift easier to spot.
- **Cost:** both workflows run on every pull request (no path filters), so required checks always
  report. This costs some CI minutes on changes that touch only one side.
- **Cost:** tooling that expects one project per repository needs the subfolder: open `frontend/` in
  Android Studio and run Gradle from `backend/` or `frontend/`.
- **Submission:** where the course asks for an "API repository" and an "Android repository", we give
  links to the `backend/` and `frontend/` folders of this repository.

## Alternatives considered

- **Two repositories:** clearer ownership, but every cross-cutting feature needed coordinated pull
  requests, and the contract would have lived in only one of them.

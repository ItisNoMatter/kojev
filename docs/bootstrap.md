# Bootstrapping kojev

One-time setup and the development phases. Standing rules live in `../AGENTS.md`.
Once every phase is complete this file has served its purpose — delete it, or keep it as history.

---

## Before starting (human)

Installing TypeSafe's official agent skill loads Jev's design guidance into the session
and noticeably improves accuracy:

```
claude plugin marketplace add typesafe-ai/skills
claude plugin install typesafe@typesafe-ai
```

Get an API key at `console.typesafe.ai` (early access is waitlisted).
Phases 1 through 5 are designed to be completable without one.

---

## Phase 1: Research

**Write no implementation code.**

Read:

1. `https://docs.typesafe.ai/llms.txt` — index of the documentation
2. `https://docs.typesafe.ai/api` — exact request and response shapes
3. `https://docs.typesafe.ai/primitives/choice`, `/primitives/score`, `/primitives/noul`
4. `https://docs.typesafe.ai/model-jaggedness/jev-1.13` — known weaknesses; this informs DSL design
5. The official SDK sources: `typesafe-ai/typesafe-sdk-js` and `typesafe-ai/typesafe-sdk-python`

Record the findings in `docs/api-notes.md` with a source URL per item. At minimum:

- The endpoint and the authentication scheme
- The complete request JSON, including how Choice, Score, and Noul questions are expressed
- The complete response JSON, noting per primitive whether a confidence field is present
- Error response shapes and status codes
- Official guidance on retries
- Model aliases (`jev-latest`, `jev-preview`) and how to pin a version
- Rate limits and documented constraints

**If the real API contradicts `../AGENTS.md`, the API wins.** Report the discrepancy before continuing,
and propose the correction to `../AGENTS.md`.

This file becomes the sole basis for everything that follows. Do not move on while it is vague.

---

## Phase 2: Skeleton

- Gradle setup, KMP targets, module layout
- ktlint
- Add `binary-compatibility-validator` and track `api/*.api` in git,
  so that public API changes appear in PR diffs. For a library this is not optional.
- CI: build and test every target, running on pull requests
- Create `CHANGELOG.md` with an empty `Unreleased` section (Keep a Changelog format)
- State in the README that breaking changes are expected while on `0.x`

Report once an empty build is green on every target and CI passes on a PR.

### Human-side tasks (Claude Code cannot do these)

- Enable branch protection on `main`: require CI, forbid force pushes and direct pushes
- Set up the Maven Central account and signing keys (needed in Phase 6)

---

## Phase 3: Domain layer

- Choice, Score, and Noul types
- `QuestionKey<T>` and typed result lookup
- Request and response serialization

Write `MockEngine` tests alongside. Base mock JSON on `docs/api-notes.md`.

---

## Phase 4: DSL layer

`JevClient { }` and `decide { }`. **This is the heart of the project.**

Build it while checking how it reads in real sample code.
If the shape should differ from the sketch in `../AGENTS.md`, propose the change with reasoning first.

---

## Phase 5: Transport

- Retries (408 / 429 / 5xx, exponential backoff with jitter, `Retry-After`)
- The error hierarchy and request ids
- Timeouts

Everything to this point must be reachable without an API key.

---

## Phase 6: Polish and release

- README: what Jev is and is not, where the key belongs, how this differs from existing clients,
  and that the project is unofficial
- `examples/`, wired into the build so it cannot rot unnoticed
- `CONTRIBUTING.md` — PR-based workflow, CI must be green
- Publish to Maven Central **from CI**. Do not adopt a workflow that publishes from a laptop.
- Tag `v0.1.0` and create a GitHub Release
- Submit to the `awesome-jev` lists (include the string `jev` in the description;
  add the topics `jev`, `typesafe`, `kotlin-multiplatform`)

---

## How to proceed

**Stop at the end of each phase, report, and wait for approval before continuing.**
Do not implement several phases in one go.

### Branching

GitHub Flow: `main` plus short-lived branches.

- **One PR per phase** as the default unit. Split further if a phase grows; keep PRs small.
- Branch names carry a type and a subject: `feat/dsl-builder`, `fix/retry-jitter`
- **No direct pushes to `main`**, even while this is a solo project.
  Changing the workflow at the moment of open-sourcing is how accidents happen.
- `main` always builds
- No `develop` branch. Release points are expressed as tags.

### Versioning

SemVer, with **breaking changes permitted throughout `0.x`**.

How the DSL reads cannot be settled without using it, so do not rush to `1.0`.
Every API change is recorded under `Unreleased` in `CHANGELOG.md`.

---

## Definition of done

- Code equivalent to the sketch in `../AGENTS.md` compiles and behaves as intended, with no casts and no `!!`
- `./gradlew build` is green on every target
- The test suite completes without an API key
- The README covers the points listed in Phase 6
- Sample code is part of the build

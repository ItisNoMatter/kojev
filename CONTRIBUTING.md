# Contributing

Thanks for looking. This is a small library with strong opinions; `AGENTS.md` is the working
agreement and the place to start - it explains what the library is for and the rules that
follow from that.

## Workflow

- `main` is protected: no direct pushes, even by maintainers. Every change is a pull request
  from a short-lived branch named `type/subject` (`feat/dsl-builder`, `fix/retry-jitter`).
- CI must be green before a PR is merged. CI runs on macOS because it is the only host that can
  build and test the iOS targets; a green `jvmTest` alone is not "tested".
- Everything committed is written in English: code comments, commit messages, PR descriptions,
  docs.

## Building

```sh
./gradlew build          # compiles every target, runs the tests, ktlint, and the API check
./gradlew apiDump        # after an intended public API change; commit api/*.api with it
./gradlew ktlintFormat   # fixes most style findings
```

`api/jvm/kojev.api` and `api/kojev.klib.api` list the public API. A PR that changes them is
changing the public API, and the diff should say so. The build fails if the dumps are stale.

## Live-API tests

Most tests run against Ktor's `MockEngine` with the exact JSON recorded in `docs/api-notes.md`,
so no key is needed. A small suite in `src/jvmLiveTest` talks to the real API:

```sh
TYPESAFE_API_KEY=... ./gradlew jvmLiveTest
```

It is part of `check`, but it is **skipped** (not failed) when `TYPESAFE_API_KEY` is unset - so
CI and a build without a key stay green. It also **runs every time** it is invoked with a key:
the task is never considered up-to-date, because what it tests is whether the API answers now,
not whether the sources changed. Don't add `--rerun`; it isn't needed.

## Examples

`examples/` is a runnable subproject that `./gradlew build` compiles, so it can't drift from the
library unnoticed. Running it calls the real API:

```sh
TYPESAFE_API_KEY=... ./gradlew :examples:run --args=triage   # triage, routing, or score
```

## The API is the source of truth

Jev's request and response shapes are recorded, with sources, in `docs/api-notes.md`. Nothing
about the API is written from memory: if you need something that isn't there, look it up
(start at `https://docs.typesafe.ai/llms.txt`), record it there first, then implement.

## Releasing

Maintainers only, and from CI only: see `docs/releasing.md`. A pushed `vX.Y.Z` tag uploads to
Maven Central; the release itself is a manual step in the Central Portal.

## Deferred decisions

Decisions that were consciously postponed are GitHub issues with the `deferred` label
(`gh issue list --label deferred`), each saying when it is to be decided and why it was
postponed. Read the one that covers what you're about to change before changing it.

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `Criterion`: implement it on your own enum to use the enum as a Choice's options or a Score's
  rubric, with the descriptions on the constants
- Typed answers: `ChoiceAnswer<T>` and `ScoreAnswer<T>` (keyed by the caller's type; a Score's
  `score` stays a `Double` and `mostLikely` is the mode), and a plain `Double` for Noul
- `QuestionKey<T>` and `Decision` for typed, exception-based result lookup (`decision[key]`),
  with a `JevResponseException` hierarchy for anything the response gets wrong
- Request/response wire serialization matching `docs/api-notes.md`
- `JevClient(apiKey, engine) { ... }`: one thread-safe client per application, on the Ktor engine
  you pass in; defaults (`https://api.typesafe.ai`, `jev-latest`, 10s) match the official SDKs
- Question builders `noul(name, instructions) { ... }`, `choice<T>(name, instructions)`,
  `choice(name, instructions, label) { X describedAs "..." }`, and `score<T>(name, instructions)`
- `jev.decide(state) { ask(...) }`: the state is any `@Serializable` value - a `String`, or a
  structure the API receives as a JSON object or array - and every question is evaluated against
  it in one request
- `Decision.usage` with the request's input and output token counts

**Temporary:** a non-2xx response currently surfaces as Ktor's own `ResponseException`
(`ClientRequestException` / `ServerResponseException`). This will be replaced by kojev's own
exception types, split by HTTP status and carrying the request id, together with the retry
policy in the next phase. Code that catches `ResponseException` from `decide` will need to change
then; it is not a stable part of the API.

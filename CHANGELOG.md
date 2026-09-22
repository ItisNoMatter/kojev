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

- `JevException` as the root of everything a decision throws: `JevApiException` with one subclass
  per HTTP status (`JevAuthenticationException`, `JevRateLimitException`, `JevServerException`,
  ...), each carrying the status, the `x-typesafe-request-id`, the raw body, and any server
  `Retry-After`; `JevRequestValidationException` exposes a 422's documented errors structured;
  `JevConnectionException` and `JevRequestTimeoutException` when no response arrived;
  `JevResponseException` (existing) now sits under the same root
- Retries with `retry { ... }` in the client config: 408 / 429 / 5xx, connection errors, and
  timeouts; exponential backoff with jitter; `retry-after-ms` / `Retry-After` honoured up to a
  cap; a total budget that bounds the whole decision - a wait that would exceed it throws the
  last failure immediately with the server's `retryAfter` on it, and the last attempt's timeout
  is shortened to what remains. Defaults match the official SDKs
- `Decision.requestId`
- A `jvmLiveTest` task that runs against the real API only when `TYPESAFE_API_KEY` is set

### Changed

- A non-2xx response no longer surfaces as Ktor's `ResponseException`; it is a `JevApiException`
  subclass. Timeouts are per attempt.

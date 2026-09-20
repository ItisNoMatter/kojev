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

There is no public way to build a question yet; that is the DSL layer, which comes next.

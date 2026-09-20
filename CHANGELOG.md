# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `NoulQuestion`, `ChoiceQuestion<T>`, `ScoreQuestion`, and their typed answers (`ChoiceAnswer<T>`,
  `ScoreAnswer`, and a plain `Double` for Noul)
- `QuestionKey<T>` via `noul()`, `choice()`, `score()`, and `Decision` for typed, exception-based
  result lookup (`decision[key]`)
- Request/response wire serialization matching `docs/api-notes.md`

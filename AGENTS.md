# kojev

A Kotlin Multiplatform client library for **Jev**, TypeSafe AI's System One model.

For first-time setup and the development phases, see `docs/bootstrap.md`.
The confirmed Jev API specification lives in `docs/api-notes.md` — read it before writing any implementation.

---

## Why this library exists

Answers to Jev questions come back as **the caller's own `enum` / `sealed` types**, not as `String`.

That is the only thing that distinguishes this library. Existing Java and Kotlin clients look up
answers by string key and cast. An implementation that merely joins that group has no reason to exist.
**Reject any design decision that erodes this property.**

Non-goals:
- Wrapping text generation (Jev does not generate)
- Prompt templates or agent frameworks
- A 1:1 port of the official SDK (`ufec/typesafe-sdk-kotlin` already does that)

---

## Hard rules

### 1. Never guess the API

Jev was released on 15 September 2026 and **does not exist in your training data**.
Writing endpoints, field names, or response shapes from memory is forbidden.

`docs/api-notes.md` is the source of truth. If you need something that is not recorded there,
look it up starting from `https://docs.typesafe.ai/llms.txt`, **update api-notes.md first**,
then implement. Mark any implementation whose source you cannot cite with a comment.

### 2. Do not confuse the three primitives

| Type | Question | Returns |
|---|---|---|
| **Choice** | Pick one option (`instructions` + a label→description map) | the label, a probability distribution, confidence |
| **Score** | Rate against a 2–10 level rubric | the score (a `Double`: the probability-weighted mean of the level numbers), the most likely level, a distribution, confidence |
| **Noul** | "Is this statement true?" | **a probability in 0..1. There is no confidence field** |

- **Noul is not a null check.** It returns the probability that a statement holds.
- **A Noul and a two-option Choice are not interchangeable.** Never carry a threshold between them.
- **A Score's answer is not a level.** The mean usually falls between levels and can land on a
  level the model gave zero probability. The library never rounds it into a level for the caller.
- An empty Choice `criteria`, or a Score with fewer than two levels, is a request-time error.

### 3. Do not weaken type safety

- `result[key]` takes a `QuestionKey<T>` and returns the answer typed as `T`
- **Do not provide string-key lookup, not even as a convenience API** — if it exists, it will be used
- A missing, mistyped, or out-of-range value in a response raises an exception.
  It is never silently turned into a default.
- The library does not pick default thresholds. That judgement belongs to the caller.

### 4. API key assumptions

The official SDK blocks direct use from browsers, because the key would be exposed.

- Treat `baseUrl` override as a first-class feature (proxies and gateways)
- **Never hardcode a key in sample code.** Read from the environment in every example.
- The iOS and Android targets exist so that domain types and the DSL can be shared with app code,
  not so that a device can call the API directly. (BYOK apps are the exception — distinguish the two in the README.)

### 5. Tests must pass without a key

Jev is in waitlisted early access. **Do not build something that stalls without a key.**

- Verify request construction and response parsing with Ktor's `MockEngine`, with no network access
- Base mock JSON on the real schema recorded in `docs/api-notes.md`. Never invent it.
- Put live-API tests in a separate source set, run only when `TYPESAFE_API_KEY` is set
  (skipped when absent — not a failure)
- A green `jvmTest` alone is not "tested". Run every target.

---

## Stack

- Kotlin 2.0+ with Coroutines
- Ktor Client (`ktor-client-core`) + `ktor-serialization-kotlinx-json`
- kotlinx.serialization

Targets: `jvm()`, `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`, `iosX64()`.
Keep platform-specific APIs out of `commonMain` so that `js(IR)` and `wasmJs` can be added later.

**Do not pin a Ktor engine.** Take no dependency on `ktor-client-okhttp` and friends; the caller injects one.

Transport requirements:
- Retry on 408 / 429 / 5xx with exponential backoff and jitter, honouring `Retry-After`. Make it configurable.
- Split errors into subclasses by HTTP status, each carrying the request id
- `JevClient` is thread-safe: create one per application and share it. `close()` releases the HTTP client it created.
- Timeouts apply per attempt

---

## Target DSL

This is a sketch of the intent, not a literal specification. If you can find a better shape
with the same properties (type-safe, declarative, pleasant in the way Compose is), propose it —
but discuss it before implementing.

```kotlin
val jev = JevClient {
    apiKey = System.getenv("TYPESAFE_API_KEY")
    baseUrl = "https://api.typesafe.ai"
    model = "jev-latest"
    timeout = 10.seconds
    engine = CIO.create()
}

// The caller's own enum carries the descriptions: a constant without one is a compile error.
enum class Intent(override val description: String) : Criterion {
    REFUND("Refunds and cancellations"),
    TECHNICAL_SUPPORT("Bugs and technical problems"),
    GENERAL_INQUIRY("Anything else"),
}

// Questions are values, usable outside the decide block
val intentQ = choice<Intent>("intent") {
    instructions = "Which team should handle this request?"
}

val angryQ = noul("is_angry") {
    instructions = "Is the customer expressing anger?"
    whenTrue = "Clear irritation or forceful tone"
    whenFalse = "Neutral or calm"
}

// Declaration order is the rubric order, lowest level first.
enum class Urgency(override val description: String) : Criterion {
    LATER("Not time-sensitive"),
    TODAY("Should be handled today"),
    NOW("Needs immediate attention"),
}

val urgencyQ = score<Urgency>("urgency") {
    instructions = "How urgently does this need a response?"
}

val result = jev.decide {
    state("Cancel my order and refund me right now.")
    ask(intentQ, angryQ, urgencyQ)
}

val intent: Intent = result[intentQ].value           // typed as Intent, no cast
val dist: Map<Intent, Double> = result[intentQ].probabilities
val conf: Double = result[intentQ].confidence

val angry: Double = result[angryQ]                    // the probability itself; no .confidence

val urgency = result[urgencyQ]                        // no .value - see the Score note below
val mean: Double = urgency.score                      // probability-weighted mean of the level numbers
val likely: Urgency = urgency.mostLikely              // the mode of the distribution
val levels: Map<Urgency, Double> = urgency.probabilities
```

Notes:
- The primary form for Choice and Score is an `enum` implementing `Criterion`: the descriptions
  live on the enum, so adding a constant without one fails to compile, and the description sits
  next to what it describes. An escape hatch keeps the explicit map form
  (`Intent.REFUND describedAs "..."`, with an explicit label) for enums the caller doesn't own,
  `sealed interface` objects, or asking the same type with a different wording.
- In the `Criterion` form the Choice wire label is the constant's `name.lowercase()`
  (`TECHNICAL_SUPPORT` → `technical_support`). The model reads labels as text next to the
  descriptions, and every official example uses lowercase words; override the label per question
  when something else is needed. Two constants that lowercase to the same label are rejected at
  construction.
- `ScoreAnswer` deliberately has no `value`. The API's answer is `score`, a `Double` mean over
  level numbers `0, 1, 2, ...` (declaration order); `mostLikely` is the highest-probability level
  (ties go to the lowest). Rounding the mean into a level is the caller's decision, never the
  library's. See `docs/api-notes.md` for the confirmed response shape.
- Confidence-gating helpers are welcome (e.g. `result[intentQ].orNull(minConfidence = 0.8)`),
  but ship no default threshold

Every question in one request is evaluated in parallel against the same state.
That is Jev's primary use, so asking several questions at once must be the natural thing to write.

---

## Naming

- The library is `kojev` (Maven: `io.github.itisnomatter:kojev`)
- Identifiers in code need not echo it. `JevClient` and `jev.decide { }` are fine —
  the same way Koin exposes `startKoin` and Ktor exposes `HttpClient`.

---

## Legal and courtesy

- MIT licensed
- If code is ported or adapted from the official SDK, preserve the upstream copyright notice in `NOTICE`
- "TypeSafe" and "Jev" are trademarks of TypeSafe AI, Inc. State in the README that this project
  is unofficial and not affiliated with or endorsed by them
- Assume an official Kotlin SDK may appear later. Do not take a namespace that would collide with it.

---

## Working agreement

- Do not fill gaps by guessing. Read the docs, or ask.
- Prefer justified code over plausible code.
- If something in this document is wrong, say so rather than following it.
- Everything committed to this repository is written in English:
  documentation, code comments, commit messages, and PR descriptions.
  This holds regardless of the language of the conversation.

## Deferred decisions

Decisions that were consciously postponed live in GitHub Issues with the `deferred` label
(`gh issue list --label deferred`), each naming the phase that decides it. `docs/bootstrap.md`
makes every phase read its own before starting. Read the recorded reasoning before touching
anything one of them covers - do not re-decide from scratch. When you postpone something, record
it the same way; a PR description or a chat is not a record.

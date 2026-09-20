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
| **Score** | Rate against a 2–10 level rubric | the score, the level legend, a distribution, confidence |
| **Noul** | "Is this statement true?" | **a probability in 0..1. There is no confidence field** |

- **Noul is not a null check.** It returns the probability that a statement holds.
- **A Noul and a two-option Choice are not interchangeable.** Never carry a threshold between them.
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

enum class Intent { REFUND, TECHNICAL_SUPPORT, GENERAL_INQUIRY }

// Questions are values, usable outside the decide block
val intentQ = choice<Intent>("intent") {
    instructions = "Which team should handle this request?"
    Intent.REFUND describedAs "Refunds and cancellations"
    Intent.TECHNICAL_SUPPORT describedAs "Bugs and technical problems"
    Intent.GENERAL_INQUIRY describedAs "Anything else"
}

val angryQ = noul("is_angry") {
    instructions = "Is the customer expressing anger?"
    whenTrue = "Clear irritation or forceful tone"
    whenFalse = "Neutral or calm"
}

val urgencyQ = score("urgency") {
    instructions = "How urgently does this need a response?"
    level(1, "Not time-sensitive")
    level(3, "Should be handled today")
    level(5, "Needs immediate attention")
}

val result = jev.decide {
    state("Cancel my order and refund me right now.")
    ask(intentQ, angryQ, urgencyQ)
}

val intent: Intent = result[intentQ].value           // typed as Intent, no cast
val dist: Map<Intent, Double> = result[intentQ].probabilities
val conf: Double = result[intentQ].confidence

val angry: Double = result[angryQ]                    // the probability itself; no .confidence
val urgency: Int = result[urgencyQ].value
```

Notes:
- Choice labels bind to `enum` values and to `sealed interface` implementations alike
- The mapping between wire labels and Kotlin identifiers is explicit (`@SerialName` or similar)
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

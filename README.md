# kojev

A Kotlin Multiplatform client for **Jev**, TypeSafe AI's System One model. You describe the
decision in your own enums; kojev asks Jev and hands the answers back in those enums.

```kotlin
enum class Intent(override val description: String) : Criterion {
    REFUND("The customer wants money returned"),
    TECHNICAL_SUPPORT("Something is broken or not working"),
    GENERAL_INQUIRY("A question, with nothing to fix or refund"),
}

val intentQ = choice<Intent>("intent", "What does the customer want?")
val angryQ = noul("is_angry", "Is the customer expressing anger?")

val result = jev.decide(ticket) { ask(intentQ, angryQ) }

val intent: Intent = result[intentQ].value      // your enum, no string, no cast
val angry: Double = result[angryQ]              // a probability
```

That snippet is compiled by the build, as
[`examples/.../snippet/ReadmeSnippet.kt`](examples/src/main/kotlin/io/github/itisnomatter/kojev/examples/snippet/ReadmeSnippet.kt)
with the imports and the surrounding `runBlocking` a real program needs, so it cannot quietly
stop matching the API.

kojev is unofficial and not affiliated with, endorsed by, or supported by TypeSafe AI, Inc.
"TypeSafe" and "Jev" are trademarks of TypeSafe AI, Inc.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.itisnomatter:kojev:0.1.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")   // any Ktor engine; kojev pins none
}
```

Targets: `jvm`, Android, `iosArm64`, `iosSimulatorArm64`, `iosX64`. kojev is on `0.x`: it follows
Semantic Versioning, and **breaking changes should be expected between any two `0.x` releases**
(see `CHANGELOG.md`).

## What Jev is, and is not

Jev doesn't generate text. It answers **typed questions** about a piece of **state** with
calibrated probabilities, in one request, all questions in parallel. There are three kinds of
question:

| Question | Asks | You get back |
|---|---|---|
| **Choice** | Which one of these options? | the option as your enum, the probability of every option, a confidence |
| **Score** | Where on this ordered rubric? | the probability of every level of your enum, the most likely level, the probability-weighted mean level number, a confidence |
| **Noul** | Is this true? | the probability that it is - and nothing else, there is no confidence for a Noul |

Two things to keep straight:

- **A Noul is not a null check.** `0.5` means "as likely as not", not "medium". Use a Score to
  measure degree.
- **A Score's answer is not a level.** The mean usually falls between levels and can even land on
  a level the model gave zero probability. kojev gives you the mean, the mode, and the
  distribution, and lets you decide which one your decision needs.

The official documentation is at <https://docs.typesafe.ai/>. kojev's own record of the request
and response shapes, with sources, is `docs/api-notes.md`.

## Using it

### Client

```kotlin
val jev = JevClient(apiKey = System.getenv("TYPESAFE_API_KEY"), engine = CIO.create()) {
    model = "jev-1.13.0"        // default "jev-latest", an alias that moves with releases
    baseUrl = "https://..."     // default https://api.typesafe.ai; override for a proxy or gateway
    timeout = 10.seconds        // per attempt
    retry { maxRetries = 3 }    // see RetryPolicy for every setting
}
```

Create one per application and share it - it is thread-safe. `close()` releases the HTTP client
it built. Everything required is a parameter; everything optional is in the lambda. That holds
throughout the DSL: forgetting something required is a compile error, not a runtime surprise.

### Questions

Your enum carries the option or level descriptions by implementing `Criterion`. A constant
without a description doesn't compile, and the description sits next to what it describes.

```kotlin
enum class Urgency(override val description: String) : Criterion {
    LATER("Not time-sensitive"),        // for a Score, declaration order is the rubric order
    TODAY("Should be handled today"),
    NOW("Needs immediate attention"),
}

val urgencyQ = score<Urgency>("urgency", "How urgently does this need a response?")

val angryQ = noul("is_angry", "Is the customer expressing anger?") {
    whenTrue = "Clear irritation or forceful tone"   // optional
    whenFalse = "Neutral or calm"
}
```

For an enum you don't own, a `sealed interface`, or a different wording of the same type, describe
the options at the question instead:

```kotlin
val toneQ = choice<Tone>("tone", "What is the customer's tone?", label = { it.wireName }) {
    Tone.Calm describedAs null
    Tone.Angry describedAs "Hostile or upset"
}
```

Questions are values. Define them once, ask them anywhere.

### State

The state is what the questions are about: any `@Serializable` value. A `String` goes out as a
JSON string; a data class or a list as a JSON object or array.

```kotlin
@Serializable
data class Ticket(val subject: String, val body: String, val plan: String, val priorContacts: Int)

val result = jev.decide(Ticket(...)) { ask(intentQ, angryQ, urgencyQ) }
```

Send a structure built for the decision, not your whole domain object: the model reads all of
it, and accuracy falls as the state grows with content unrelated to the questions.

### Answers

```kotlin
val intent = result[intentQ]           // ChoiceAnswer<Intent>
intent.value                           // Intent
intent.probabilities                   // Map<Intent, Double>
intent.confidence                      // Double, 0..1

val urgency = result[urgencyQ]         // ScoreAnswer<Urgency> - deliberately no .value
urgency.mostLikely                     // Urgency: the mode
urgency.probabilities                  // Map<Urgency, Double>
urgency.score                          // Double: the mean level number, 0..levels-1

result[angryQ]                         // Double: the Noul probability itself

result.model                           // the versioned model that actually answered
result.usage.inputTokens               // every response carries token usage
result.requestId                       // for support requests
```

That is the whole way to read an answer. There is no lookup by string id, no default threshold,
and no helper that rounds a Score into a level. Where a decision needs a threshold, the number is
yours, written where the decision is made:

```kotlin
if (intent.confidence >= 0.8) route(intent.value) else escalate()
val atLeastToday = urgency.probabilities.filterKeys { it >= Urgency.TODAY }.values.sum()
```

The threshold that is right for a refund is not the one that is right for a page to on-call.
kojev doesn't know which one you're writing.

### Errors and retries

Everything a decision throws is a `JevException`:

- `JevApiException` - a non-2xx status, one subclass per status (`JevAuthenticationException`,
  `JevRateLimitException`, `JevServerException`, ...), each with the status, the
  `x-typesafe-request-id`, the raw body, and any server `Retry-After`. A 422 exposes the API's
  validation errors structured.
- `JevConnectionException` / `JevRequestTimeoutException` - no response at all.
- `JevResponseException` - a 2xx that doesn't match what was asked (a missing answer, an unknown
  label, a value out of range). Nothing is ever silently turned into a default.

Retries happen before any of these reaches you: 408, 429, 5xx, connection errors, and timeouts,
with exponential backoff and jitter, honouring `Retry-After`, under a total budget for the whole
decision. The defaults are the official SDKs'; `retry { ... }` changes them. A wait the budget
can't afford is not taken - the failure is thrown at once with the server's `retryAfter` on it,
so the decision to wait longer is yours too.

## Where the API key belongs

On a server. Jev's API is called with a bearer key, and the official SDK refuses to run in a
browser for exactly that reason. kojev's Android and iOS targets exist so that the domain types
and the questions can be shared with app code - the app calls *your* backend, which holds the key
and calls Jev. The exception is a bring-your-own-key app, where the user supplies their own key;
then the device calling Jev directly is the point. Know which of the two you are building.

Never put a key in code. The examples and tests read `TYPESAFE_API_KEY` from the environment.

## How kojev differs from other Kotlin clients

Two other Kotlin clients are published, and each is good at what it chooses to be. If you are on
the JVM and want more conveniences, jev4k is the one to look at.

| | kojev | [jev4k](https://github.com/pambrose/jev4k) | [typesafe-sdk-kotlin](https://github.com/ufec/typesafe-sdk-kotlin) |
|---|---|---|---|
| Platforms | JVM, Android, iOS | JVM | JVM, Android |
| A Choice is answered as | your enum | your enum, or a string by id | a string key |
| A Score is answered as | a distribution over your enum, the most likely level as your enum, the mean | the mean, a distribution by level number, a legend, plus the nearest and most likely level numbers | the mean, a distribution by level number, a legend |
| Descriptions on the enum | required (`Criterion`) | optional (`JevOption`) | not applicable |
| State | any `@Serializable` value | a string, a `JsonElement`, or any `@Serializable` value | a string or a `JsonElement` |
| Reading an answer | one typed way | by typed handle or by string id | by typed handle |
| Threshold helpers | none; the number is always yours | yes, with defaults (`isTrue(threshold)`, `band()`) | none |
| Distribution | Maven Central | Maven Central | JitPack |

typesafe-sdk-kotlin is a faithful port of the official JavaScript SDK (its `NOTICE` says so); if
you want the official SDK's shape in Kotlin, that is the one. kojev's narrowness is a choice, not
an omission: one typed way to read an answer, and the judgement calls left to the code that
knows what they cost.

## Development

```sh
./gradlew build                                  # every target, tests, ktlint, API check
TYPESAFE_API_KEY=... ./gradlew jvmLiveTest       # against the real API; skipped without a key
TYPESAFE_API_KEY=... ./gradlew :examples:run     # runnable examples in examples/
```

See `CONTRIBUTING.md`; releases are described in `docs/releasing.md`. The development history and
phases are in `docs/bootstrap.md`.

## License

MIT. See `LICENSE`.

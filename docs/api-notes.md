# Jev API notes

Confirmed findings from the official documentation, the published OpenAPI spec, and the
official SDK sources. Every statement below has a source. Nothing here is guessed.

Sources consulted:

- [S1] https://docs.typesafe.ai/llms.txt
- [S2] https://docs.typesafe.ai/api
- [S3] https://docs.typesafe.ai/primitives/noul
- [S4] https://docs.typesafe.ai/primitives/choice
- [S5] https://docs.typesafe.ai/primitives/score
- [S6] https://docs.typesafe.ai/model-jaggedness/jev-1.13
- [S7] https://docs.typesafe.ai/models
- [S8] https://docs.typesafe.ai/confidence
- [S9] https://api.typesafe.ai/openapi.json
- [S10] https://github.com/typesafe-ai/typesafe-sdk-python — `src/typesafe_sdk/_core/errors.py`,
  `src/typesafe_sdk/_core/constants.py`, `src/typesafe_sdk/_core/retry.py`,
  `src/typesafe_sdk/_schemas/models.py` (generated from the same OpenAPI spec as [S9])
- [S11] https://github.com/typesafe-ai/typesafe-sdk-js — `src/client.ts`, `src/retry.ts`

---

## Endpoint and authentication

- Base URL: `https://api.typesafe.ai` [S2][S11]
- `POST /v1/systemone` — answer one or more questions about the content in `state` [S2][S9]
- `GET /v1/models` — list the models and aliases available to the authenticated account [S9]
- Auth: `Authorization: Bearer <API_KEY>` header [S2]

---

## Request shape

Top level (`SystemOneRequest`, from the OpenAPI schema) [S9][S10]:

```json
{
  "state": "string | object | array — required",
  "model": "string — required, e.g. \"jev-latest\"",
  "questions": {
    "<caller-chosen key>": { "...one of the three question types below" }
  }
}
```

`questions` must have at least one entry [S9][S10]. `state` and each question's `instructions`
may be a string, object, or array [S9][S10].

### Choice request

```json
{
  "state": "Help! My payouts have been failing for 3 days.",
  "model": "jev-latest",
  "questions": {
    "department": {
      "type": "choice",
      "instructions": "Which team should handle this?",
      "criteria": {
        "billing": "Payments, invoicing, refunds",
        "technical": "Bugs, outages, integrations",
        "sales": "Pricing, upgrades, new accounts"
      }
    }
  }
}
```
[S2]

`criteria` is a map of option name → description. A description may be `null` (option name alone
is used), a string, an object, or an array. Up to 255 options [S4][S9].

### Score request

```json
{
  "state": "The export button crashes the settings page in Safari. It works in Chrome, but a few of our customers only use Safari.",
  "model": "jev-latest",
  "questions": {
    "bug_severity": {
      "type": "score",
      "instructions": "How severe is the reported issue?",
      "criteria": [
        "Cosmetic; no impact to functionality",
        "Broken or degraded feature, but workaround exists",
        "Blocking issue; no workaround exists"
      ]
    }
  }
}
```
[S5]

`criteria` is an **ordered array** of level descriptions. A level's position determines its score,
starting at zero — there is no way to assign arbitrary/non-contiguous level numbers. Minimum 2
levels, maximum 10 [S5][S9].

### Noul request

```json
{
  "state": "Help! My payouts have been failing for 3 days.",
  "model": "jev-latest",
  "questions": {
    "is_urgent": {
      "type": "noul",
      "instructions": "Does this convey urgency?",
      "criteria": {
        "true": "Explicitly time-sensitive",
        "false": "No urgency expressed"
      }
    }
  }
}
```
[S2]

`criteria` is optional and, when present, is a `{ true, false }` object of descriptions, each
optional independently [S3][S9].

---

## Response shape

Top level (`SystemOneResponse`) [S9][S10]:

```json
{
  "model": "jev-latest",
  "answers": { "<question key>": { "...one of the three answer types below" } },
  "usage": { "input_tokens": 312, "output_tokens": 48 }
}
```

`model` is the **resolved, versioned** model name that actually answered — it may differ from an
alias supplied in the request [S9][S10]. `answers` has at least one entry, keyed by the same names
used in the request's `questions` map, and each answer's `type` matches its question's `type` [S9].

`usage.input_tokens` is the number of billable input tokens; `usage.output_tokens` is the number
of output tokens used to answer, which the schema states are "currently free of charge" [S9][S10].

### Choice answer

```json
{
  "type": "choice",
  "choice": "technical",
  "probabilities": { "billing": 0.08, "technical": 0.85, "sales": 0.07 },
  "confidence": 0.82
}
```
[S2][S9]

- `choice`: the option with the highest probability
- `probabilities`: sums to approximately 1.0
- `confidence`: **present**, 0–1, derived from the shape of `probabilities`

### Score answer

```json
{
  "type": "score",
  "score": 1.3,
  "confidence": 0.54,
  "legend": {
    "0": "Cosmetic; no impact to functionality",
    "1": "Broken or degraded feature, but workaround exists",
    "2": "Blocking issue; no workaround exists"
  },
  "probabilities": { "0": 0.0, "1": 0.7, "2": 0.3 }
}
```
[S5][S9]

- `score`: a **float**, the probability-weighted mean of the level numbers — can fall between
  integer levels, is not itself an integer level index [S5][S9]
- `legend`: the requested `criteria`, re-keyed by level number as a **string**
- `probabilities`: same string-number keys as `legend`, sums to approximately 1.0
- `confidence`: **present**, 0–1

### Noul answer

```json
{
  "type": "noul",
  "noul": 0.98
}
```
[S2][S3][S9]

- `noul`: 0–1 probability of "yes" / "true"
- **No `confidence` field.** Confirmed explicitly in two independent places:
  - the primitives doc: "Noul does not return a separate confidence value" [S3]
  - the confidence doc, parenthetically, about Noul: "(Noul answers don't carry one.)" [S8]
  - and structurally: `NoulAnswer` in the OpenAPI schema has only `type` and `noul`, no
    `confidence` field, unlike `ChoiceAnswer` and `ScoreAnswer` [S9][S10]

### Confidence semantics (Choice and Score)

Confidence is derived from the shape of `probabilities`: concentrated on one outcome → high
confidence; spread out → low confidence. It is computed identically for Choice and Score [S8].
Documented threshold guidance: high confidence → act automatically; medium → confirm/flag/gather
more info; low → do not act, route to a human. "A confidence threshold is not one number" —
different actions should be gated at different levels depending on the cost of a wrong
answer [S8]. This matches `AGENTS.md`'s existing rule that the library ships no default threshold.

---

## Errors

Documented status codes [S2][S9]:

| Status | Meaning |
|---|---|
| 400 | Bad request (seen in SDK error hierarchy [S10]; not separately called out in the prose docs) |
| 401 | Missing or invalid API key |
| 403 | Access denied (SDK error hierarchy only [S10]) |
| 404 | Not found (SDK error hierarchy only [S10]) |
| 422 | Request validation failed (missing required field, malformed question, etc.) |
| 429 | Rate limit exceeded — back off and retry |
| 5xx (incl. 529 "Overloaded") | TypeSafe is temporarily overloaded / failed to process the request — retry after a short delay |

Only `422`'s error body is formally documented in the OpenAPI spec (`HTTPValidationError`) [S9]:

```json
{
  "detail": [
    { "loc": ["body", "questions", "urgency", "score", "criteria"], "msg": "Field required", "type": "missing", "input": { "type": "score" }, "ctx": { "min_length": 1 } }
  ]
}
```

Other error bodies (400/401/403/404/429/5xx) are **not** formally specified in the OpenAPI
document [S9]. Observed shapes, from the official Python SDK's own message-extraction logic and
its tests (which is the SDK author's own accommodation for body shape, not a documented
guarantee) [S10]:

- A plain string body, or
- `{"message": "..."}`, or
- `{"error": "..."}` or `{"error": {"message": "..."}}`, or
- `{"detail": "..."}` or `{"detail": {"message": "..."}}` (as well as the 422 list form above)

Both official SDKs read the request id from an `x-typesafe-request-id` response header on every
response, error or success, and surface it on thrown errors [S10][S11]. There is no request id
field inside the JSON error body itself — it is header-only.

**Observed, not documented** (one live `401` on 2026-09-22 with an invalid key, via `curl`):

```
HTTP/1.1 401 Unauthorized
content-type: application/json
x-typesafe-request-id: req_01a0c710a5dc7f06a2d40f76c0f18f01

{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}
```

So at least `401` uses the `detail.message` shape from the list above, the request id looks like
`req_` + 32 hex characters, and there is an `error_type` field that neither the docs nor the SDKs
mention. One observation of one status; do not build on `error_type` until it is documented.

**This is a gap worth flagging explicitly in the discrepancy section below**, not a place to
invent a schema.

---

## Rate limits

No specific numeric rate limits (requests/min, tokens/min, tiers) are published anywhere checked:
not in the prose docs [S2], not in the OpenAPI spec [S9]. The only documented guidance is
qualitative: a `429` means the limit was exceeded and the caller should back off and retry [S2].
Do not hardcode a limit; only react to `429` and `Retry-After`.

## Retries

- Prose docs: recommend "exponential backoff instead of retrying immediately" for `429`/`5xx`;
  state the SDKs handle this automatically [S2].
- Both official SDKs implement retry the same way, confirming a de facto standard even though it
  is not written in the prose docs [S10][S11]:
  - Retryable statuses: `408`, `429`, and the entire `500`–`599` range (this covers `529`)
  - Default `max_retries`: **2** (so 3 attempts total)
  - Backoff: initial delay **0.5s**, doubling each attempt, capped at **5.0s**, with jitter that
    randomly subtracts up to **25%** of the computed delay
  - A server-supplied delay takes priority over computed backoff when present, read from two
    headers: `Retry-After` (seconds, or an HTTP date) and a TypeSafe-specific `Retry-After-Ms`
    (milliseconds) [S10]
  - Default total retry budget: **30 seconds** across the whole call, including the initial
    attempt (Python SDK; stops before a retry that would exceed the budget) [S10]. The JS SDK
    has **no** total budget; its per-call `timeout` is the only outer bound [S11]
  - The JS SDK ignores a server-supplied delay longer than **60 seconds** (`maxRetryAfterMs:
    60_000`) and falls back to its computed backoff instead [S11]; the Python SDK has no such cap
  - Connection errors and timeouts are retried by default in addition to the status-code list
    [S10][S11]
  - Both SDKs send `X-TypeSafe-Retry-Count: <n>` on every retry (n = 1 for the first retry),
    and not on the initial attempt [S10][S11]

None of the specific numbers above (2 retries, 0.5s/5.0s/25% backoff, 30s budget, 60s cap) are
stated in the prose documentation — they come only from reading both SDKs' source. Where the two
agree, treat it as "the de facto official default"; where they differ (budget, cap), a client has
to choose. Neither is a documented contract.

## Model aliases

- `jev-latest` and `jev-preview` both currently resolve to the versioned model `jev-1.13.0` [S7]
- An alias "moves when a new release ships, so the answers behind it can change without a change
  on your side" [S7] — use the versioned id (e.g. `jev-1.13.0`) to pin, especially if thresholds
  were tuned against a specific version [S7]
- `GET /v1/models` returns the list of currently available names/aliases, each with `name`,
  `description`, `release_date` [S9]
- The response's top-level `model` field always reports the versioned id that actually answered,
  even when the request specified an alias [S9][S10] — use this to detect a silent alias move

## Official SDK client defaults

Both official SDKs ship the same defaults, so a client that copies them behaves like the vendor's
own (not documented in prose anywhere; read from source) [S10][S11]:

| Setting | Default | Python (`src/typesafe_sdk/constants.py`) | JS (`src/client.ts`, `src/retry.ts`) |
|---|---|---|---|
| Base URL | `https://api.typesafe.ai` | `DEFAULT_BASE_URL` | `DEFAULT_BASE_URL` |
| Model | `jev-latest` | `DEFAULT_MODEL` | `DEFAULT_MODEL` |
| Request timeout | **10 seconds** | `DEFAULT_TIMEOUT = 10.0` | `DEFAULT_TIMEOUT_MS = 10_000` |

Both strip a trailing `/` from the base URL before appending paths [S10][S11]. Both also read the
key, base URL, and model from environment variables (`TYPESAFE_API_KEY`, `TYPESAFE_BASE_URL`,
`TYPESAFE_DEFAULT_MODEL`) [S10]; kojev does not, since environment access is platform-specific
and the caller passes the key in.

## Known model weaknesses (jev-1.13) — relevant to DSL/design decisions

From the model-jaggedness doc [S6]:

- Literal interpretation: answers the question as written, not as intended; take care with
  negations and implied scope in `instructions`
- Poor at arithmetic, counting, and date/time comparison — do not lean on Jev for these
- Numeric encodings (hex, RGB, binary) underperform semantic descriptions in `criteria`/`state`
- Multi-hop reasoning and double negatives lose accuracy
- Large amounts of state irrelevant to the question reduce accuracy (acts as a distractor)
- Score outputs are not numerically calibrated between threshold levels
- Not adversarially robust: does not treat `state` as untrusted, can be steered by
  injected instructions or misleading framing inside `state`
- Contradictory instructions/criteria degrade performance
- No guaranteed structural invariants between separate Noul/Choice questions — e.g. probabilities
  across two "opposite" questions do not necessarily sum to 1
- Not trained for text generation — confirms `AGENTS.md`'s existing non-goal

---

## Discrepancies between `AGENTS.md` and the real API

1. **Score level numbering.** `AGENTS.md`'s DSL sketch shows `level(1, "...")`, `level(3, "...")`,
   `level(5, "...")` — implying the DSL author picks arbitrary, non-contiguous level numbers. The
   real API has no such concept: `criteria` is a plain ordered array, and level numbers are
   **always** `0, 1, 2, ...` assigned by position [S5][S9]. Any `level(n, ...)`-style builder must
   either enforce contiguous zero-based numbering or translate caller-chosen numbers to positions
   before sending the request — this is a Phase 4 design decision, not a Phase 1 fix, but it must
   be resolved before that builder is implemented. Flagging per `docs/bootstrap.md`'s instruction
   to report contradictions before continuing; not proposing a resolution here.

2. **Score value type.** `AGENTS.md`'s hard-rules table just says Score "Returns... the score" —
   compatible with the real API, but worth being explicit: `score` is a **float** (probability-
   weighted mean), not necessarily one of the integer level numbers [S5][S9]. Wherever this
   library ends up mapping a Score answer, `result[scoreQ].value` cannot be typed as the caller's
   level enum directly the way Choice can be typed as the caller's enum — a Score's value is a
   continuous number, and only `legend`/`probabilities` map back to the caller's level
   descriptions. This doesn't contradict `AGENTS.md` outright, but the DSL sketch's
   `val urgency: Int = result[urgencyQ].value` is inconsistent with a float `score` unless the
   library intentionally floors/rounds — a decision to make explicitly in Phase 4, not silently.

Everything else checked — the Choice/Score/Noul field sets, the "Noul has no confidence" rule,
the request/response shapes, the transport hard rules (retry on 408/429/5xx with backoff and
jitter, honouring `Retry-After`, error subclasses per status carrying a request id) — **matches
`AGENTS.md` with no contradiction found.**

---

## Build system note (reported, not acted on)

This repository is configured as an **Amper** module (`module.yaml`, `product: kmp/lib`), not a
Gradle project, despite `AGENTS.md` and `docs/bootstrap.md` (Phase 2) being written entirely in
Gradle terms (`./gradlew build`, Gradle-specific tooling). This is not a Phase 1 concern to fix,
but it is recorded here because it will block Phase 2 verbatim-as-written. See the PR description
for the detailed findings — no build configuration was changed in this branch.

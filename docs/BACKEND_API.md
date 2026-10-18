# Try-on backend contract

The Android app never talks to an AI vendor directly and never contains an API key. It talks to **your
backend**, which holds the vendor credentials and calls the AI provider (Replicate, fal.ai, Vertex AI,
a self-hosted model, …). Swapping vendors changes only the backend.

Set the base URL at build time (never committed — `local.properties` is git-ignored):

```
# local.properties  (or ~/.gradle/gradle.properties)
tryon.backendUrl=https://api.example.com
```

With no URL configured the app builds and runs, but **refuses to generate** and says so. It never fakes a result.

Release builds only allow HTTPS. Debug builds additionally allow cleartext to `10.0.2.2` (the emulator's
alias for the dev machine) so the mock backend in `tools/mock-backend/` can be used.

## Headers sent by the app

| Header | Meaning |
|---|---|
| `Idempotency-Key` | The app's job id (UUID). **Must be honoured**: a repeat `POST` with the same key must return the existing job, not start (and bill) a second generation. This is what makes retrying a timed-out upload safe. |
| `X-Install-Id` | Random per-install UUID. Not a secret — use it for rate limiting. Authenticate real abuse protection server-side (e.g. Play Integrity); do not rely on this header. |

## Endpoints

### `POST /v1/tryon/jobs` — start a generation
`multipart/form-data`: `person` (JPEG), `garment` (JPEG), `category` (`tops|bottoms|dresses|outerwear|full_outfit`).
Images are already validated, EXIF-rotated and downscaled to ≤1536 px on the long edge.
Returns `202` (or `200` for an idempotent replay) with a job object.

### `GET /v1/tryon/jobs/{id}` — status
Returns a job object. Polled every 2 s for up to 5 min.

### `DELETE /v1/tryon/jobs/{id}` — cancel (best effort)
Any `2xx`. The app has already marked the job cancelled locally.

### `GET {resultUrl}` — result image
Absolute URL or a path relative to the base URL. Must return image bytes (the app verifies it decodes).

## Job object

```json
{ "id": "r-123", "status": "queued|processing|succeeded|failed|canceled",
  "progress": 0-100,                      // optional, only while processing
  "resultUrl": "/v1/files/r-123.jpg",     // required when succeeded
  "error": { "message": "no person detected" } }   // optional when failed
```

## Errors

| HTTP | App behaviour |
|---|---|
| `429` (+ `Retry-After` seconds) | "Too many requests", retryable |
| `5xx` | "Service ran into a problem", retryable |
| `400/413/415/422` | Shown as rejected images with `error.message`; **not** retryable |
| `401/403` | Rejected; not retryable |
| `404` on status | Treated as a provider failure |
| Network drop / timeout | "Can't reach the service" / "took too long", retryable |

Error bodies may carry `{ "error": { "message": "…" } }`; the message is shown to the user, so keep it human-readable
and free of internal detail.

## Data handling the backend must honour

The app tells users their two images are uploaded only when they tap **Generate**, used for that request, and
that saved looks stay on the device. The backend and its AI vendor must actually behave that way: don't retain
images longer than needed to produce and deliver a result, don't train on them without consent, and document the
vendor's retention in your privacy policy. The in-app privacy text (`settings_privacy_body`) must be updated if
your vendor's real behaviour differs.

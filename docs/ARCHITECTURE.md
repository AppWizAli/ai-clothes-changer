# Architecture

Native Android, Kotlin, Views + ViewBinding (the stack the project started with), **MVVM** with unidirectional
data flow: Fragment → ViewModel → use case / repository → data source, state flowing back as `StateFlow`.
Hand-written DI (`AppContainer`) — the graph is small and annotation processors would add build risk for no gain.

```
presentation/   Fragments + ViewModels + UI state          (Android UI; knows domain, never data)
domain/         models, use cases, repository & provider interfaces, validation   (pure Kotlin)
data/           repository implementations, file stores, HTTP provider, image processing
core/           AppConfig, AppError / Outcome, clock & id abstractions
work/           WorkManager worker + scheduler (background execution)
```

`domain` has no Android imports. `presentation` talks to `domain` interfaces; only `AppContainer` wires `data` in.

## Key decisions

* **One source of truth for a generation: the persisted `TryOnJob`.** The UI only observes it. A
  `RunGenerationUseCase` run by a WorkManager worker drives it (QUEUED → UPLOADING → GENERATING →
  COMPLETED/FAILED/CANCELLED). Leaving the screen, killing the app, or rotating changes nothing; reopening just
  re-attaches. WorkManager re-runs interrupted jobs after process death.
* **Idempotent, never-double-billed retries.** The job id is the `Idempotency-Key`. Once the provider accepts a
  job its `remoteId` is stored; retry resumes *polling* it instead of submitting again. If the submit response
  was lost, the retry resends the same key.
* **Cancellation is race-safe.** `TryOnJobRepository.update` is atomic and lets a transform veto; workers only
  write while the job is still active, so a cancel can never be overwritten by late progress or a late result.
* **Honest results.** Completed means a file was downloaded *and* decodes as an image. Saved means the look
  file was fully written (atomic temp-file + rename). No placeholders, no fake success.
* **Provider isolation.** `TryOnProvider` is the only seam to the AI service. `HttpTryOnProvider` speaks the
  contract in `BACKEND_API.md`; `NotConfiguredTryOnProvider` fails honestly when no backend is set.
* **Privacy.** No storage/camera permissions (system photo picker; camera via FileProvider URI). Backup disabled
  for app data. Only normalised JPEGs are kept; originals are never stored. Image bytes are never logged.
* **Ads.** None, and no ad SDK. Monetisation, if ever added, belongs in its own module behind an interface.

## Storage layout (app-private)

| Dir | Contents | Lifetime |
|---|---|---|
| `files/work` | normalised photo/garment chosen by the user | until replaced + swept |
| `files/jobs` | one JSON per job | last 10 finished jobs |
| `files/results` | generated images not yet saved | with their job |
| `files/looks` | **saved looks** + `index.json` | until the user deletes them |
| `cache/capture`, `cache/incoming` | camera target / import staging | cleared on use / Settings |

My Looks keeps only metadata in memory and loads thumbnails lazily in a `RecyclerView`.

## Build setup notes

* AGP 9.1.1 bundles Kotlin 2.2; current libraries are built with Kotlin 2.4, so the root `build.gradle.kts`
  raises the compiler via the buildscript classpath.
* `compileSdk 37` is required by Coil 3.6 / OkHttp 5.5; `targetSdk` stays 36.

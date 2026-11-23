# Sprint 4 · local processing contract

Mission KNOWLEDGE-HUB-ANDROID-V1, [issue #6](https://github.com/loftfull/new3/issues/6), decision authored by Codex on 2026-11-23. [Product architecture](ARCHITECTURE.md).

Capture commits the original first. WorkManager then drains pending processing. The worker is unique and serial within the app. A batch uses the existing private Room database. Processing records live separately from material data, notes and manual taxonomy. Startup re-enqueues unfinished items. A provider exception records a failure and offers a user-controlled retry. Neither state changes the originals or removes search access.

The included provider derives an extractive summary and keyword/topic suggestions from the material already on-device. A URL by itself has no generated summary. It does not retrieve pages, transcribe videos, run OCR, or call a hosted AI model. `MaterialAnalyzer` and the WorkerFactory define the provider replacement boundary. External transfer would need separate opt-in under CAP-14.

Acceptance checks cover deterministic text analysis, metadata-only handling, storage preservation under a failed provider, offline retry, and additive migration to the processing records table. The full AI and visual gates require additional proof.

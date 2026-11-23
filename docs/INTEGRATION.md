# Android library integration · 2026-11-23

Mission KNOWLEDGE-HUB-ANDROID-V1. Codex authored the integration contract. [Issue #18](https://github.com/loftfull/new3/issues/18).

This branch combines the locked S01 Compose library with the storage changes from [#13](https://github.com/loftfull/new3/pull/13), [#14](https://github.com/loftfull/new3/pull/14) and [#17](https://github.com/loftfull/new3/pull/17). Design, lifecycle and capture checks for the separate [S01 PR](https://github.com/loftfull/new3/pull/16) are recorded at its tested revision.

- Collection actions create manual or source-based entries, manage membership without deleting materials, and open favorites and read-later.
- Material detail persists notes with revision checks, exposes read-later, records history, and links locally related materials by shared source, manual labels or recognized topic.
- WorkManager preserves processing status independently of originals. The active provider uses available local text and metadata for extractive hints. It performs no network transfer.
- Settings exports a consistent JSON snapshot of materials and notes to a user-selected destination. It records attachment names/MIME types and leaves original attachment bytes in local storage. Archive restoration, complete backup and device transfer are separate work.

Local verification command:

```sh
./gradlew :app:assembleDebug :app:lintDebug :core:lintDebug :core:testDebugUnitTest :app:assembleDebugAndroidTest :core:assembleDebugAndroidTest
```

The device gate uses the opt-in `android-device-qa` job. Runtime visual comparison beyond the compact S01 smoke, full taxonomy and project UI, real provider-backed AI, analytics, complete backup/restore and release remain open.

Independent review found that leaving a material could discard an unsubmitted note draft. The correction retains editor state per material and adds the `keepsDraftAfterReturningToSameMaterial` Compose regression. The full host build and both Android test packages were rebuilt after the fix.

## Sprint 5 operational subset

[Issue #20](https://github.com/loftfull/new3/issues/20). Projects use separate typed collection membership. Moving a material out of a project leaves the original in the library. Local analytics shows total materials, a seven-day count, favorites, completed local processing and counts by captured source. It reads from Room-backed state.

Local APK, 5 JVM tests, app/core lint and Android test APK assembly passed. The independent review checked enum compatibility, smart collections, membership SQL and protection of originals. The test `projectPersistsAndRemovingMembershipPreservesSourceMaterial` is compiled. The scope still has the shared collection/project name constraint and a seven-day cutoff calculated when analytics opens. Full S18/S23 visual coverage and guided knowledge assistance are separate gates.

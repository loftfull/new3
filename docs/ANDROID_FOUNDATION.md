# MISSION: KNOWLEDGE-HUB-ANDROID-V1

Mode: AUTONOMOUS / QUALITY-GATED / EVIDENCE-REQUIRED.

## Baseline and authority

- GitHub main was inspected at 3b4d476d17af5319584d27b5d0e6d1243cfa35b2.
- Planning snapshot 4145e0a299995c38e2e02870c6d30afb9d39c3a8 was copied from the existing design branch into 271ba11. [PR #1](https://github.com/loftfull/new3/pull/1) remains a draft and separate from the development branch.
- The ARENA source was accessible. Its own status documents do not establish a working runtime in this Linux environment. The live MOZG/Obsidian vault was unavailable. The GitHub package and authorized archived governance guidance inform the fallback process. No ARENA runtime events have been recorded.
- The planning package lists earlier concept and screen images without repository paths. Owner selection and Reference Lock are pending.

## Decisions

| Date | Author | Decision and basis | Record |
|---|---|---|---|
| 2026-11-23 | Codex | Establish a native greenfield Android project. The inspected repository contains planning material and no prior application or database. Current mission authorizes the stack. | [Architecture](ARCHITECTURE.md) |
| 2026-11-23 | Codex | Use Room for offline storage and FTS. Keep attached content in private app storage. Preserve every original when exact matches exist. | [Capture issue](https://github.com/loftfull/new3/issues/4) |
| 2026-11-23 | Codex | Use provisional light Material components for exercising the runtime navigation and capture. Owner selection gates visual implementation and comparison. | [Design](../DESIGN.md) |

## Functional contract

An Android user may share text, an HTTP(S) URL or a permitted content URI, or add them inside the app. The preview must present the import before commitment. Saving a supported batch writes the original values locally before any enrichment. An unreadable item reports failure without partially creating library records. A reopening user can find saved items without a network, search indexed content and open the original data. Exact matches are recorded as separate materials. An empty library provides a working Add action.

## Task graph

1. Foundation and minimal CI, tracked in [#8](https://github.com/loftfull/new3/issues/8) and [#7](https://github.com/loftfull/new3/issues/7). Verify debug APK, runtime start, build, lint and unit tests.
2. Capture and storage, tracked in [#4](https://github.com/loftfull/new3/issues/4). Verify Android share ingestion, offline restart and file access.
3. In parallel with the independent core, [#3](https://github.com/loftfull/new3/issues/3) provides concepts for owner selection. Selection precedes lock, approved specifications and [#5](https://github.com/loftfull/new3/issues/5).
4. Library and reading build on persistent materials and search. Processing and duplicate review [#6](https://github.com/loftfull/new3/issues/6) build on their data contracts. Project, analytics and knowledge workflows build on processing provenance.
5. Export and restoration use the stable schema. Release requires device, migration, accessibility, reference comparison and independent review evidence. See [Delivery roadmap](DELIVERY_ROADMAP.md).

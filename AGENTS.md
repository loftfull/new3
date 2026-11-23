# Repository agent instructions

Follow .github/GITHUB_WORKFLOW.md before any change.

Project scope: Android-first personal knowledge library; see docs/PRODUCT_VISION.md and docs/MASTER_PROMPT.md.

1. Preserve original user data, URLs, notes and history.
2. Do not infer implementation status from mockups.
3. Work in a task branch and open a draft PR for incomplete work.
4. Do not overwrite main, approved visual references or existing code without a reviewed plan.
5. One full-screen mobile PNG per screen; no collages; log provenance and SHA-256.
6. Prioritize core capture/storage reliability over AI features.
7. Require explicit authorization for destructive migrations, publication, private data exposure or paid services.
8. Report exact tests run and unverified claims.
9. Keep GitHub Actions low-cost: read-only permissions, bounded time, avoid unnecessary artifact uploads.
10. Respect Android one-handed UX, accessibility and local-first privacy.

## New-project stage gates

Check the live ARENA/FCC and the owner-configured MOZG vault first. Record the active rule source and revision. Use the repository rules and documented fallback when live sources are unavailable, and keep that limitation visible. [Current handoff](docs/GOVERNANCE_RECOVERY.md)

For product-facing work, apply the sequence Product Contract → Design Reference → UI Specification → Design System → Visual Shell → Functional Integration → Regression QA → Release. Check each screen against the manifest and the applicable owner-approved reference. A technical spike or fixture cannot close a product-facing gate. For visual comparisons record the Git revision, device API, viewport, font scale, seed state, raw Android screenshot and independent findings. Update the issue and evidence before advancing the gate. [S01 UI contract](docs/UI_SPEC.md)

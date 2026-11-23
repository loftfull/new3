# Acceptance Matrix — Android-first

| ID | Scenario | Pass criteria | Evidence |
|---|---|---|---|
| CAP-01 | Android Share Sheet URL | URI accepted, local record created before AI starts | device test + DB assertion |
| CAP-02 | Offline save | Original URL available after app restart without network | emulator airplane-mode test |
| CAP-03 | Duplicate exact URL | Detect normalized URL; no destructive merge | unit test |
| CAP-04 | Similar article | Suggest relation, do not auto-delete | fixture + UI test |
| CAP-05 | AI failure | Material remains searchable, processing status visible | failure injection |
| CAP-06 | Manual topic | AI cannot silently overwrite user's selection | unit test |
| CAP-07 | Search | Title, text, tags, source filters; deterministic results | test dataset |
| CAP-08 | Notes | Edits persist and survive backup/restore | integration test |
| CAP-09 | Export | All URLs, notes, collections preserved in round trip | export/import diff |
| CAP-10 | Mobile navigation | Five destinations consistent; child screen contextual actions | screenshot + UI test |
| CAP-11 | Accessibility | 48dp targets, TalkBack labels, large fonts without clipping | accessibility checks |
| CAP-12 | Design fidelity | Screen screenshots reviewed against approved references | visual QA report |
| CAP-13 | Data migration | Old data preserved; rollback possible | migration test |
| CAP-14 | Privacy | Explicit opt-in before external AI transfer | privacy test |
| CAP-15 | Background processing | Jobs retry safely, show status, obey Android restrictions | WorkManager test |

Statuses: NOT_STARTED / IN_PROGRESS / VERIFIED / BLOCKED. All currently NOT_STARTED pending code and device build.

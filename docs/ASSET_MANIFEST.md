# Asset manifest and provenance

## Sources
- User-provided screenshot in ChatGPT conversation: current mobile screen «Темы», two-column content cards, source tabs. **Not yet uploaded to repository.**
- Prior generated concept images: visual exploration only; **not uploaded and not approved**.
- Existing repository README and GitHub workflow rules: inspected.

## Planned structure
design/reference/original/
design/concepts/01..10/
design/screens/S01..S28/
design/states/
design/components/
design/reviews/
design/manifest.json

## Every design asset requires
asset_id, screen_id, source, file_path, dimensions, sha256, approval_status, created_at, design_version, notes.

## Quality gates
- One PNG = one full mobile screen, no collages.
- Editable design source required for approved screen.
- Original reference never overwritten.
- Reference Lock only after owner selects a direction.
- Generated illustrative text/data must not be confused with real user content.
- Do not commit personal photos or private saved materials into a public repository without explicit approval.

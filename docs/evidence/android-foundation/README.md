# Android Foundation device evidence

Captured by GitHub Actions on the Android 15 (API 35) emulator at source commit `559434b084ecadf86906b78a1786bd7bd78c6dda` on 2026-11-23. The app was built on the runner and installed with ADB. The runner used its own debug key. Its APK SHA-256 was `b5484afe51c9ae2725887ee9998cfcffa6ff164e6005b05af57f11265a6888af`. [Device run](https://github.com/loftfull/new3/actions/runs/45121581236) · [Test and screen ZIP](https://github.com/loftfull/new3/actions/runs/45121581236/artifacts/14794610245).

| Evidence PNG | Scene | SHA-256 |
| --- | --- | --- |
| [S01-library.png](S01-library.png) | App launch, empty library | `edca87e01df24e1b67983a7ad547c9b2d4222e662842ecc9bd041b088c107e0c` |
| [S07-share-preview.png](S07-share-preview.png) | Android implicit SEND opens import preview | `641961b18677e9007849bfe447f812240fcde892c4dbdb743abd9b637915ae02` |
| [S01-offline-restart.png](S01-offline-restart.png) | Library after save, force-stop and relaunch with airplane mode active | `29830d5e979ac80b1380e1fcce64668a355872dea0c912642929e606f88284f6` |
| [S13-original-link.png](S13-original-link.png) | Exact saved link shown after relaunch | `e3fc6a83295582e1c35a1b811b693e3a640e6c207e3fb917a7b4957a52ccaa87` |

The 320×640 captures depict the functional Foundation shell. The approved S01-v1 design was recorded separately and its Compose implementation has a separate verification gate. The compact Foundation bottom bar wraps the Russian library label.

# Security and privacy

Delphis is a free, open-source companion for the Dolphin emulator. This page says what it can access and why,
what it never does, how to check that a download is genuine, and how to report a problem privately.

## Reporting a problem privately

If you find a security problem, please **don't open a public issue**. Use **Security → Report a vulnerability**
on this repository instead ([direct link](https://github.com/HiramRG/Delphis/security/advisories/new)). Only the
maintainer can see it. You'll get an answer as soon as possible, and a fix goes to the beta channel first, then
to everyone.

Only the newest stable and beta builds get fixes, so please update before reporting.

## What Delphis can access, and why

| Access | Why |
| --- | --- |
| **Dolphin's user folder** (you pick it once) | Delphis writes Dolphin's controller profiles, per-game settings and its own picture shaders there, reads save-state times for autosave, and signs Dolphin in to RetroAchievements when you ask. |
| **Your games folders** (read only) | To list your games and read each game's ID. Delphis never changes or uploads game files. |
| **Accessibility helper** (you turn it on) | Presses Dolphin's own "Exit Emulation", Quick Save and Quick Load buttons, so games close cleanly and autosave works. On phones and tablets it also shows the Delphis pill and catches the menu buttons while a game runs. It only reads Dolphin's own screens, and, when Delphis closes Dolphin to change its touch-button setting, the Recent apps screen. Of other apps it only notices which one is in front (to hide the pill); it never reads their screens, never logs what's on screen, and never sends anything anywhere. |
| **Install apps** | To install Delphis' own updates from GitHub. Before installing, Delphis checks the download came from GitHub, is Delphis, is signed with Delphis' key and isn't an older build; Android checks the signature again. |
| **Internet** | GitHub (update checks and downloads), RetroAchievements (your progress and badges, only if you sign in) and GameTDB (cover art). Nothing else. |
| **Vibrate** | The "Try it" rumble test in Settings. |
| **Close background apps** | On Android 13 and older, ends Dolphin's background process when you exit Delphis. |
| **Network state** | To know whether you're online (achievements work offline from a saved copy). |

The trackpad presets use a small server that only listens inside the device (127.0.0.1) and only sends
trackpad movement to Dolphin. It can't be reached from the network.

## What Delphis never does

* No ads, no analytics, no tracking, no accounts of its own.
* No data leaves your device except the requests listed above.
* Diagnostics stay on your device. They only leave when you copy or share a report, or use *Report a problem*,
  and then your account names, keys, tokens and folder locations are hidden.
* Your RetroAchievements web API key is stored encrypted with a key that never leaves your device. Delphis never
  stores your RetroAchievements password: it's only used once to sign Dolphin in.

## Checking a download

Every APK is built from this repository by GitHub Actions, not on anyone's computer, and signed with Delphis' key.
Each release lists:

* the **SHA-256 checksum** of `Delphis.apk` (also in `Delphis-SHA256SUMS.txt`): compare it with
  `sha256sum Delphis.apk`;
* the **signing certificate's SHA-256 fingerprint**: compare it with
  `apksigner verify --print-certs Delphis.apk`;
* a **proof of origin** (build provenance), signed by GitHub, that ties the APK to the exact commit and build
  that made it: `gh attestation verify Delphis.apk -R HiramRG/Delphis`.

Android also refuses to install an update signed with a different key, so an installed copy of Delphis can only
ever be updated by a genuine build.

## How the code is kept safe

* Every change runs the full build, unit tests and the layout check before anything is published.
* GitHub's code scanner (CodeQL) checks the code on every change to `main` and weekly.
* Dependabot warns about libraries with known security problems and proposes updates.
* Every build step from outside the repository is pinned to an exact version.

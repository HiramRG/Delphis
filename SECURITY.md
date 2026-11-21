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
| **Accessibility helper** (you turn it on) | Presses Dolphin's own "Exit Emulation", Quick Save and Quick Load buttons, so games close cleanly and autosave works. While a game runs it watches the controller's buttons for the quick save / load buttons (Select + L1 / R1, unless you turn them off) and, on phones and tablets, the menu buttons; it shows the Delphis pill there too. Every other button goes straight to the game; Diagnostics only notes which buttons the first few presses were, to help with controller problems. On Android 11 and up it takes a small picture of the game's screen when a save is made (or when Delphis' menu opens over the game, for a save made from the menu): only of the game's screen, only while the game is what's on it, kept only in Delphis' own storage on the device, never shared, and left out of backups. It only reads Dolphin's own screens, and, when Delphis closes Dolphin to change its touch-button setting, the Recent apps screen of your home app. Of other apps it only notices which one is in front (to hide the pill); it never reads their screens, never presses anything in them (Back is only sent while Dolphin or Delphis is in front), never logs what's on screen, and never sends anything anywhere. |
| **Install apps** | To install Delphis' own updates from GitHub. Before installing, Delphis checks the download came from GitHub, is Delphis, is signed with Delphis' current key and isn't an older build; Android checks the signature again. (A few older Androids can't read a signature before installing; there, Android's own check is the one that applies.) |
| **Internet** | GitHub (update checks and downloads), RetroAchievements (your progress and badges, only if you sign in) and GameTDB (cover art). Nothing else. |
| **Vibrate** | The "Try it" rumble test in Settings. |
| **Close background apps** | On Android 13 and older, ends Dolphin's background process when you exit Delphis. |
| **Network state** | To know whether you're online (achievements work offline from a saved copy). |

The trackpad presets use a small server that only listens inside the device (127.0.0.1), so it can't be reached
from the network. It sends trackpad movement to one listener at a time (Dolphin), and ignores malformed messages
from other apps on the device.

## What Delphis never does

* No ads, no analytics, no tracking, no accounts of its own.
* No data leaves your device except the requests listed above.
* Diagnostics stay on your device. They only leave when you copy or share a report, or use *Report a problem*,
  and then account names (current and past), keys, tokens, folder and file paths, and names inside controller
  names ("Ana's controller") are hidden.
* Your RetroAchievements web API key is stored encrypted with a key that never leaves your device; if Android
  can't encrypt it, it isn't saved at all. Delphis never stores your RetroAchievements password: it's only used
  once to sign Dolphin in.
* Backups of Delphis (Android's cloud backup or moving to a new phone) carry your settings and presets, but not
  logs, achievement progress or autosave copies. The encrypted key can't be read on another device.

## Known limitations

Things worth knowing before you install, stated plainly:

* **The accessibility helper is powerful.** Android lets any accessibility service read and act on every app.
  Delphis limits itself in its own code (see the table above), but Android can't enforce that: you're trusting
  the code, which is public here. You can turn the helper off in Android's Accessibility settings at any time;
  Delphis still launches games, but can't autosave, close games cleanly or open its menu on phones and tablets.
* **Dolphin keeps its RetroAchievements sign-in unencrypted.** When you sign Dolphin in from Delphis, the token
  is saved where Dolphin itself saves it, in plain text in Dolphin's `Config/RetroAchievements.ini`. That's
  Dolphin's design, the same as signing in from Dolphin. Signing out (in Delphis or Dolphin) removes it.
* **Some services learn which games you have.** Cover art is downloaded from GameTDB by game ID, and
  RetroAchievements sees the games you play while signed in, as it does for any emulator.
* **No store review.** Delphis isn't on Google Play, so no store checks each release. What you can check instead
  is under "Checking a download" below.
* **Signing keys.** Delphis' first key (`delphis`) was public at one point, so it was replaced by `delphis-2`;
  copies updated since then refuse updates signed only with the first key, and Delphis' own updater refuses them
  too. `delphis-2` is kept in this repository encrypted, with its password only in GitHub's secrets. Moving to a
  new key that never touches the repository is planned.
* **Other apps can ask Delphis to start a game.** That's how game frontends launch Wii and GameCube games through
  Delphis, so it needs no special permission. Delphis only accepts game files, but a request does close the game
  you're playing.
* **Older releases included a debug build** (`Delphis-debug.apk`), meant for testing only. Releases from
  November 2026 on publish only `Delphis.apk`, and test builds are now a separate app that can't install over
  Delphis. Don't install the old debug build.

## Checking a download

Every APK is built from this repository by GitHub Actions, not on anyone's computer, and signed with Delphis' key.
Each release lists:

* the **SHA-256 checksum** of `Delphis.apk` (also in `Delphis-SHA256SUMS.txt`): compare it with
  `sha256sum Delphis.apk`;
* the **signing certificate's SHA-256 fingerprint**: compare it with
  `apksigner verify --print-certs Delphis.apk`;
* a **proof of origin** (build provenance), signed by GitHub, that ties the APK to the exact commit and build
  that made it: `gh attestation verify Delphis.apk -R HiramRG/Delphis`.

Android also refuses to install an update signed with a different key, so an up-to-date copy of Delphis can only
be updated by a build signed with Delphis' current key.

## How the code is kept safe

* Every change runs the full build, unit tests and the layout check before anything is published.
* GitHub's code scanner (CodeQL) checks the code on every change to `main` and weekly.
* Dependabot warns about libraries with known security problems and proposes updates.
* Every third-party GitHub Action used in the build is pinned to an exact version. (The Android build tools and the
  build machine come from GitHub's own runners.)
* Only builds that get published are signed, and the build tools have stopped running before the key is used.

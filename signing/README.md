# Signing

Android only installs an update over an existing app when the update is signed by the same key
(or by a newer key the app has rotated to). The in-app updater relies on this.

- `delphis-keys.p12.gpg` is a PKCS12 keystore encrypted with GPG (AES-256). Its password, which
  also opens the keystore itself, exists only as the repository secret **`SIGNING_PASSWORD`**
  (Settings → Secrets and variables → Actions). Without it the file is useless.
- The keystore holds two keys:
  - `delphis`: the original key. It was once public, so it is no longer trusted on its own.
  - `delphis-2`: the current key.
- CI builds the APKs unsigned, then signs them with `apksigner` using a key-rotation lineage
  (`delphis` → `delphis-2`, APK Signature Scheme v3). Installed copies accept the update and move to
  the new key; after that, an APK signed only with the old key can't replace them, because the
  old key isn't allowed to roll back.
- If the secret is missing, `main` builds fail before publishing; other branches still build and
  test but produce an unsigned APK.

Local builds (`gradle assembleRelease`) produce an unsigned release APK and a debug APK signed
with Android's debug key; neither can update an installed copy from the releases page.

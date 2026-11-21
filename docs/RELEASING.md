# Releasing

A release is a GitHub release with one signed APK attached: `aura-farm-vX.Y.Z.apk`. It is built by the **Release** workflow
(`.github/workflows/release.yml`), which runs the unit tests first and publishes only if they pass.

## How a release starts

Either of these starts it:

- **Commit a new version tag into the `RELEASE` file** (for example `v0.7.0`) and push. The workflow runs whenever `RELEASE` changes.
- **Push a git tag** like `v0.7.0`.

**The `RELEASE` trigger fires on a push to *any* branch**, and builds from that commit. So do not edit `RELEASE` on a feature branch to
"get it ready": it would publish from the branch before review. The normal sequence is:

1. Merge the pull request into `main`.
2. In a separate commit on `main`, change `RELEASE` to the new tag (the history shows these as "Release v0.6.0").
3. Watch the workflow. When it finishes, check the release (below).

A release that already exists is left alone ("nothing to do"), so re-pushing is harmless. To redo a version you must delete the
GitHub release and its tag first; it is usually better to cut the next patch version.

## Version numbers

The tag is `vMAJOR.MINOR.PATCH`. The workflow derives Android's version code from it: **`major*10000 + minor*100 + patch`** (v0.6.0 is
600, v1.2.3 is 10203). It always rises with the version, so a newer release installs over an older one. Never reuse or lower a code.
(An earlier scheme used the workflow's run number and produced a release with code 3, below the test builds already on a phone.)

## Signing

An APK installs over an earlier one only if both are signed with the **same key**. Releases are signed with a stable release
key held in four GitHub repository secrets (Settings, Secrets and variables, Actions):

| Secret | |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64 encoded (`base64 -w0 release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | Its password |
| `RELEASE_KEY_ALIAS` | The key's alias |
| `RELEASE_KEY_PASSWORD` | The key's password |

The release key is the maintainer's own debug keystore, reused, so every test build the maintainer has installed is signed with the
same key as the releases: the certificate's SHA-256 starts `e99254b4…` (you can verify an APK with
`apksigner verify --print-certs file.apk`). **Without those secrets the workflow still works but signs with a throwaway key**, and
that APK will not install over any other build (it says so in the release notes).

**Custody.** The keystore and the passwords are kept outside the repository, in the maintainer's password manager. They must never be
committed (`.gitignore` excludes `*.jks` and `*.keystore`). If the key is lost, every existing install has to be uninstalled before a
new key's release can be installed, which wipes the saved planks, so tell people to **Back up data** first. To rotate it deliberately,
give the app a new application id (see below) instead.

## Checklist for a release

Before:

- [ ] `gradle :core:test` passes (CI will run it too, but find out first).
- [ ] The [device checklist](DEVICE-CHECKLIST.md) has been done on a phone, on a build as close to the release as possible.
- [ ] [CHANGELOG.md](../CHANGELOG.md) has an entry for this version, and `docs/release-notes/vX.Y.Z.md` has the full notes.
- [ ] If a data format changed, the compatibility tests are green and the release notes say what to do about old data.
- [ ] The README and the docs describe the app as it now is.

After the workflow finishes:

- [ ] The release exists, is not a draft, and has `aura-farm-vX.Y.Z.apk` attached.
- [ ] Download it and check: `aapt dump badging` shows the right package, version name and version code (code = formula above), and
      `apksigner verify --print-certs` shows the release certificate (`e99254b4…`).
- [ ] Installs over the previous release on a phone, and the planks are still there.
- [ ] Replace the workflow's generic release text with the real notes (copy `docs/release-notes/vX.Y.Z.md` into the release).

## Release notes

Two places, for two readers: [CHANGELOG.md](../CHANGELOG.md) is a short list of what changed, release by release; `docs/release-notes/`
holds the full text for each release as it should appear on the GitHub release page (what's new, how to install or upgrade, known
issues). The workflow currently writes a generic body (install steps and a note about on-device processing); paste the real notes
over it after publishing. Making the workflow use the notes file automatically is on the [roadmap](ROADMAP.md).

## The package name change

Up to v0.5.0 the app was **Coach Chicken**, application id `com.coachchicken`. From v0.6.0 it is **Aura Farm**, application id
**`com.aurafarm`**.

Android identifies an app by its application id, so Aura Farm is a *different app* to Android: it installs next to Coach
Chicken instead of replacing it, has its own empty storage, and cannot read the old app's files. The way across is the backup
that already existed:

1. In Coach Chicken: menu, **Back up data**, and save the zip.
2. Install Aura Farm.
3. In Aura Farm: menu, **Restore data**, and choose the zip. It merges and never overwrites.
4. Check the calendar, then uninstall Coach Chicken (otherwise both apps send reminders).

Backups from every earlier version restore (the format did not change). The Kotlin package in the source is still `com.coachchicken`:
the source package is not what Android sees, and renaming every file would be a large change for no user-visible benefit.

**Treat the application id as permanent from here.** Changing it again strands everyone's data behind a manual backup and restore.

## If something goes wrong

- *The workflow failed on the tests:* nothing is published. Fix on a branch, merge, and start again (change `RELEASE` to the same
  tag if no release exists yet, or the next patch version).
- *The APK installs fresh but not over the previous release:* the signing key or the version code differs. Check both with
  `apksigner` and `aapt`. If the secrets were missing, the build used a throwaway key: add them and release the next patch version.
- *A bad release is published:* mark it as a pre-release or delete it from the Releases page and cut the next patch version; do not
  reuse the number.

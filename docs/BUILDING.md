# Development and build

## Tools

- JDK 17.
- Android SDK Platform 35.
- The repository's Gradle Wrapper (Gradle 8.13) and Android Gradle Plugin 8.13.2.

The app is written in Java 17. There are no separate runtime libraries in the
Gradle dependencies; the bot uses Android APIs.

## Debug build

From the repository root in Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it over a connected device with ADB:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Both build variants use the same saved key from the local `local-signing.properties`
file. The key is not generated during the build. If the config is missing,
Gradle stops to avoid accidentally signing an update with a different key. The
config and keystore are ignored by Git. Config fields:

```properties
storeFile=/absolute/path/to/persistent-signing.jks
storePassword=YOUR_EXISTING_PASSWORD
keyAlias=YOUR_EXISTING_ALIAS
keyPassword=YOUR_EXISTING_PASSWORD
storeType=JKS
```

An update requires the same package ID and key pair. Debug uses package ID
`ua.borlock.autotap.newkey` and the “new” app label; release uses
`ua.borlock.autotap`.

## Structure

```text
app/src/main/java/ua/borlock/autotap/   Java UI, recognition, and solver code
app/src/main/res/                       Android resources and AccessibilityService configuration
docs/                                   User guides and architecture notes
gradle/wrapper/                         Gradle Wrapper version
```

`local.properties`, generated `build/` and `.gradle/` directories, APKs, and
Codex attachments are ignored by Git. Do not commit Android settings XML dumps
from the repository root.

## Current configuration

- `applicationId`: `ua.borlock.autotap`.
- App version: `1.9.46` (`versionCode` 77).
- `minSdk`: 24; AccessibilityService screenshots require Android 11 / API 30 or later.
- `targetSdk` / `compileSdk`: 35.

## Checks

```bash
bash gradlew testDebugUnitTest --no-daemon --max-workers=1
bash tools/check-merge-madness.sh
```

The second command needs `JAVA_HOME` and `ANDROID_HOME` or `ANDROID_SDK_ROOT`.
It runs the recognizer code compiled for the APK: checks every cell against
manually verified screenshot matrices, rejects covered/cropped frames, and
checks scaling and learning objects from hints without static samples. Separate
synthetic scenarios test a newly revealed silhouette and technical capacity for
positions 1–17 in four rows. The actual game has 15 ranks; these synthetic
samples do not verify recognition of all real late-game artwork. JVM tests do
not dispatch a physical Android gesture; that needs a phone.

Timer tests use Robolectric 4.14.1 with Android API 33; Android resources are
enabled for unit tests through `unitTests.includeAndroidResources`. The first
run downloads Gradle dependencies and the Robolectric Android image, so it
needs network access. In an environment requiring an HTTP proxy, pass its JVM
settings according to that environment's policy. The signing key is also
required to configure tests; do not create a new key to work around a missing
local configuration.

For an APK that updates the main app, use release:

```bash
bash gradlew testDebugUnitTest assembleRelease --no-daemon --max-workers=1
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Before distributing, compare the package ID and certificate SHA-256 with the
previous APK, and increment `versionCode`. Debug's `.newkey` suffix cannot
update the release app.

Performance measurements and comparison with the previous engine are in
[VERIFICATION-1.9.35.md](VERIFICATION-1.9.35.md).

The module catalog and settings are documented in [MODULES.md](MODULES.md),
the implemented interface in [INTERFACE-1.9.37.md](INTERFACE-1.9.37.md), panel
controls in [INTERFACE-1.9.38.md](INTERFACE-1.9.38.md), compact buttons and
algorithm checks in [ALGORITHMS-1.9.39.md](ALGORITHMS-1.9.39.md), Backpack
Blitz autoplay goals in [BLITZ-AUTOPLAY-1.9.40.md](BLITZ-AUTOPLAY-1.9.40.md),
search and cascade improvements in [BLITZ-AUTOPLAY-1.9.41.md](BLITZ-AUTOPLAY-1.9.41.md),
reward timers and checks in [TIMERS-1.9.42.md](TIMERS-1.9.42.md), Ukrainian, Czech, and English localization in
[LOCALIZATION-1.9.46.md](LOCALIZATION-1.9.46.md), rank-15 testing in
[ALGORITHMS-LEVEL15.md](ALGORITHMS-LEVEL15.md), future clicker ideas in
[ROADMAP.md](ROADMAP.md), and the PackMate visual refresh in
[REDESIGN-1.9.46.md](REDESIGN-1.9.46.md).

## Download and install offline

The signed release APK is available in the repository at
[`downloads/PackMate-1.9.46.apk`](../downloads/PackMate-1.9.46.apk) and from
the download link in the [README](../README.md).
Download the APK to an Android device, open it, and allow the file manager or
browser to install unknown apps if Android asks. The APK can be installed
without a network connection. The app does not request internet access.

The release APK is signed with the persistent project key. An update installs
over an existing copy only when its application ID and signing certificate
match. Keep using the official release APK to preserve update compatibility.

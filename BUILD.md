# Arro-POS — Build & APK Guide

Everything you need to compile this app and produce an installable `.apk`,
step by step — locally on your machine, and with GitHub Actions (the way you
already build it).

---

## 1. What this app is built with

| Piece | Version | Where it's set |
|---|---|---|
| Language | Kotlin + Jetpack Compose | `app/src/main/java/...` |
| Android Gradle Plugin (AGP) | `9.1.1` | `gradle/libs.versions.toml` → `agp` |
| Gradle | `9.3.1` | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | `2.2.10` | `libs.versions.toml` → `kotlin` |
| KSP (annotation processor) | `2.3.5` | `libs.versions.toml` → `googleDevtoolsKsp` |
| Compose BOM | `2024.09.00` | `libs.versions.toml` → `composeBom` |
| Room (local DB) | `2.7.0` | `libs.versions.toml` → `roomRuntime` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | `app/build.gradle.kts` |
| Java toolchain (CI) | JDK 17 | `.github/workflows/build-apk.yml` |
| Application id | `com.aistudio.lkpos.wkyvbc` | `app/build.gradle.kts` |

The database schema is version **7** (`CURRENT_DB_VERSION` in
`data/db/PosDatabase.kt`).

---

## 2. Before you build — what this project needs

1. **JDK 17** (the CI job uses Temurin 17).
2. **Android SDK with platform 36** (`compileSdk 36`, `minorApiLevel 1`).
   Android Studio installs this for you.
3. Internet access to `dl.google.com`, `repo1.maven.org` / `mavenCentral()`,
   and `services.gradle.org` (Gradle itself + dependencies).
4. **No `google-services.json` needed.** The project is set to build even when
   the Firebase config file is missing:
   - `gradle.properties` → `googleServices.missing.passthrough=true`
   - `app/build.gradle.kts` → `googleServices { missingGoogleServicesStrategy = WARN }`
5. **No `.env` needed to compile.** The Secrets plugin falls back to
   `.env.example` (see `.env.example`). `GEMINI_API_KEY` is only required at
   *runtime* by the Gemini feature, not to build.

> ⚠️ **Important — the Gradle Wrapper is not committed.**
> This repo has `gradle/wrapper/gradle-wrapper.properties` but **not** the
> `gradlew` / `gradlew.bat` scripts or `gradle/wrapper/gradle-wrapper.jar`.
> That means:
> - On your own machine you cannot run `./gradlew` until you generate the
>   wrapper (step 3 below), or you run a locally installed Gradle 9.x.
> - The GitHub Actions workflow still works because it pins `gradle-version:
>   '9.3.1'` in `gradle/actions/setup-gradle@v3`, which downloads Gradle for
>   the runner.

---

## 3. Build locally (command line)

### 3.1 One-time: generate the Gradle wrapper

From the project root, with a JDK 17 on your `PATH` and either Android Studio's
bundled Gradle or a system Gradle:

```bash
# If you have Gradle installed:
gradle wrapper --gradle-version 9.3.1
```

Or, easiest: open the project once in **Android Studio**, let it sync, then run
the same `gradle wrapper` task from its terminal. After this you will have
`gradlew` and `gradle/wrapper/gradle-wrapper.jar`, and every future build is:

```bash
./gradlew :app:assembleDebug
```

### 3.2 Compile & produce the APK

```bash
# Debug APK — signed with the auto-generated debug key, installs on any phone:
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK — needs a signing keystore (see section 5):
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Other useful tasks:

```bash
./gradlew :app:build          # assemble + run unit tests + lint
./gradlew :app:test           # run the JVM unit tests only
./gradlew :app:lint           # static analysis (catches real problems)
```

### 3.3 Verify sources without the Android SDK

The repo ships `tools/check_sources.py`, a static checker that catches the
mistakes that break a compile (unbalanced braces, a file that no longer parses,
a symbol used without an import) even on a machine with **no Android SDK**:

```bash
pip install tree-sitter tree-sitter-kotlin
python3 tools/check_sources.py
```

It is a heuristic, not a compiler — treat its output as hints, not verdicts.
(At the time of writing it reports 2 known false positives: `SuggestionChip` in
`LowStockRestockDialog.kt`, which is actually Material3's chip, and `timestamp`
in `SetupValidationTest.kt`, which is a constructor argument.)

---

## 4. Build with GitHub Actions (the way you already do)

The workflow lives in `.github/workflows/build-apk.yml`.

**How it works:**

1. Triggers on push to `main`/`master` **or** the **"Run workflow"** button
   (`workflow_dispatch`) in the *Actions* tab.
2. Checks out the repo, installs **JDK 17**, pins **Gradle 9.3.1**, and runs
   `gradle :app:assembleDebug --no-daemon`.
3. Uploads `app-debug.apk` as a downloadable artifact
   (`PosApp-Debug-APK`, kept for 14 days).

**To build and download an APK:**

1. Push to `main` (or open *Actions → Build & Release Android APK → Run workflow*).
2. Wait for the run to go green.
3. Open the run → scroll to **Artifacts** → download `PosApp-Debug-APK`.
4. Unzip and sideload `app-debug.apk` onto your phone (allow "Install unknown
   apps" on the device).

> The debug APK is signed with a generated debug key, so it installs on any
> phone but is **not** for Play Store. For a store-ready, signed release APK,
> see section 5.

---

## 5. Signing & a release APK

`app/build.gradle.kts` already defines a `release` signing config that reads
from environment variables:

| Env var | Meaning |
|---|---|
| `KEYSTORE_PATH` | Path to your `.jks` keystore (defaults to `my-upload-key.jks` in the repo root) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |
| (alias) | Fixed to `upload` |

### Locally

```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=change-me
export KEY_PASSWORD=change-me
./gradlew :app:assembleRelease
```

### On GitHub Actions (recommended)

Add these as **repository secrets** (Settings → Secrets and variables →
Actions → New repository secret): `KEYSTORE_BASE64`, `STORE_PASSWORD`,
`KEY_PASSWORD`. Then add a step to the workflow that decodes the keystore and
a release-build step. Example:

```yaml
      - name: Restore signing keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: echo "$KEYSTORE_BASE64" | base64 -d > my-upload-key.jks

      - name: Build Release APK
        env:
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: gradle :app:assembleRelease --no-daemon
```

> **Never commit a keystore or its passwords.** `.gitignore` already ignores
> `debug.keystore`.

---

## 6. First build: commit the Room schemas

The first successful build generates Room schema JSON files under
`app/schemas/com.example.data.db.PosDatabase/` (because `exportSchema = true`
and `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`). These **must
be committed** — they are what let Room verify migrations at build time and
what the migration tests run against. After your first green build, you will
see new `*.json` files there; add them in the same commit. Do not edit them by
hand.

---

## 7. Troubleshooting the build failures you've hit

| Symptom | Cause & fix |
|---|---|
| `gradle: command not found` on GitHub Actions | The wrapper scripts aren't committed. The workflow now pins `gradle-version: '9.3.1'` in `setup-gradle`. |
| `./gradlew: No such file or directory` locally | Generate the wrapper first: `gradle wrapper --gradle-version 9.3.1` (section 3.1). |
| "Could not find com.android.application" / plugin resolution error | AGP 9.1.1 must download from `google()`. Ensure network to `dl.google.com`; if blocked, the build cannot resolve plugins. |
| `signingConfig` / keystore error on `assembleRelease` | `STORE_PASSWORD` / `KEY_PASSWORD` / `KEYSTORE_PATH` are empty. Either set them (section 5) or build `assembleDebug`, which uses the debug keystore. |
| Firebase / `google-services` error | You should **not** see one — passthrough is enabled. If you do, confirm `googleServices.missing.passthrough=true` is in `gradle.properties`. |
| "Could not connect to Kotlin compile daemon" | Already mitigated: `gradle.properties` sets `kotlin.compiler.execution.strategy=in-process`. |
| OOM / `Metaspace` during compile | `gradle.properties` sets `org.gradle.jvmargs=-Xmx4g`. If your machine is small, lower it, but 4g is the tested default. |
| Room migration test fails | A `CURRENT_DB_VERSION` bump without a matching entry in `Migrations.kt`. Add the migration; don't enable destructive fallback. |
| Slow first build | The first run downloads Gradle + every dependency and generates Room schemas. Expect several minutes once, then it's cached. |

---

## 8. Quick reference — the full happy path

```bash
# 1. One time only
gradle wrapper --gradle-version 9.3.1      # creates ./gradlew

# 2. Build (debug, installable on any phone)
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 3. Optional sanity check without the SDK
pip install tree-sitter tree-sitter-kotlin
python3 tools/check_sources.py
```

For the Play-Store / signed path, combine sections 5 and 2.

# Arro-POS — Google Drive OAuth Setup & Google Sign‑In Prompt

> Use this as a hand‑off prompt to an implementer (or an AI agent) working on the
> Arro‑POS Android app.
>
> **Goal:** replace the current AccountManager‑based Google Drive token flow with
> a real Google OAuth 2.0 client ID and Google Sign‑In, so the first upload to
> Google Drive is reliable on a fresh Android install.

---

## 1. Background — why this work exists

The app already has a provider‑controlled, per‑device backup to Google Drive:

- `app/src/main/java/com/example/data/cloud/GoogleDriveCloudTransport.kt`
  - Uploads a `arro-pos-backup-<timestamp>.zip` snapshot to a per‑device Drive
    folder.
  - Uses `AccountManager.blockingGetAuthToken(...)` with scope
    `oauth2:https://www.googleapis.com/auth/drive.file`.
- `app/src/main/java/com/example/data/cloud/CloudSettingsRepository.kt`
  - Stores the provider master switch, provider Gmail, provider access code
    hash, owner Gmail, last backup/sync status.
- `app/src/main/java/com/example/data/cloud/CloudBackupManager.kt`
  - Creates rolling local backups (with `PRAGMA wal_checkpoint(FULL)` before
    copying the SQLite database) and computes a data snapshot hash.
- `app/src/main/java/com/example/data/cloud/CloudSyncWorker.kt` +
  `CloudSyncScheduler.kt`
  - Hourly sync, daily local backup, and manual sync (WorkManager).
- `app/src/main/java/com/example/ui/screens/more/CloudBackupScreen.kt`
  - Owner‑visible Backup & Cloud screen and hidden provider screen.
- `app/src/main/java/com/example/ui/viewmodel/PosViewModel.kt`
  - `setOwnerGmail(...)`, `backupNow()`, `syncNow()`, `saveProviderCloud(...)`,
    `unlockProvider(...)`, `googleAccounts()`.

### Known limitation

On some devices `AccountManager.blockingGetAuthToken(...)` returns a token and
works. On other devices, or if the app is installed fresh with a different
signing certificate than the one registered with Google, Google rejects the
token request because the app is not registered for the OAuth scope. The result
is that the first Google Drive upload is blocked until a proper OAuth client is
configured.

---

## 2. What to build

### 2.1 Google Cloud Console setup (provider action, done once per release)

1. Create or open a **Google Cloud project** owned by the POS provider.
2. If not already enabled, enable:
   - **Google Drive API** (`https://www.googleapis.com/auth/drive.file` is a
     Drive‑scoped scope).
3. Create an **OAuth 2.0 client ID**:
   - Type: **Android application**.
   - Package name: `com.aistudio.lkpos.wkyvbc` (the current `applicationId` in
     `app/build.gradle.kts`).
   - SHA‑1 certificate fingerprint: from the **debug keystore** for local builds
     and from the **upload/release keystore** for production builds.
   - Add both (or maintain separate clients) because debug and release builds
     have different fingerprints.
4. Copy the **OAuth client ID** into a build secret or `.env` file. It must not
   be hard‑coded into the repository.

### 2.2 Add dependencies

Add the official Google Sign‑In / credential manager dependencies to
`app/build.gradle.kts` and `gradle/libs.versions.toml` (the commented‑out
blocks already hint at these):

- `androidx.credentials:credentials`
- `androidx.credentials:credentials-play-services-auth`
- `com.google.android.libraries.identity.googleid:googleid`
- If you prefer the classic Play Services Sign‑In:
  `com.google.android.gms:play-services-auth`
- `androidx.activity:activity-compose` (already present) for the launcher.

Recommended modern approach: **AndroidX Credentials Manager** with Google ID
Token, using the OAuth client ID, obtaining an ID token, then exchanging it for
OAuth scopes (or requesting the `drive.file` scope through the browser flow on
Android). A practical simpler approach that is still reliable:

- Use `GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)`
  `.requestEmail().build()` to sign in and get the Google account.
- Then use `accountManager.blockingGetAuthToken()` only for accounts returned
  by a real successful Google sign‑in.
- For the most robust Drive upload, use a **browser‑based OAuth consent flow**
  (for example via AppAuth or Credential Manager) with the scope
  `https://www.googleapis.com/auth/drive.file`, and store the refresh token in
  `EncryptedSharedPreferences`.

### 2.3 Create a GoogleAuthManager / GoogleDriveAuthManager

New file, for example:

```
app/src/main/java/com/example/data/cloud/GoogleDriveAuthManager.kt
```

Responsibilities:

- `suspend fun signIn(result: ...): GoogleUseResult`
  - Opens Google Sign‑In / Credential Manager.
  - Returns the signed‑in email, display name, and an access/refresh token for
    the `drive.file` scope.
- `suspend fun getStoredToken(): String?`
  - Reads the stored token from `EncryptedSharedPreferences`.
- `suspend fun refreshTokenIfNeeded(): String?`
  - Refreshes the OAuth token when the stored one has expired.
- `fun signOut()`
  - Clears stored token/session, but **does not delete the shop database or
    local backups**.

### 2.4 Replace the transport token source

In `GoogleDriveCloudTransport.kt`:

- Keep the upload/list/create‑folder logic as is.
- Replace the public entry point that takes a raw token with a method that
  first asks `GoogleDriveAuthManager` for a valid token, then continues.

Suggested changes:

```kotlin
suspend fun upload(
    accountEmail: String,
    deviceName: String,
    fileName: String,
    file: File
): FileMetadata? {
    val token = authManager.validToken(accountEmail) ?: return null
    ...
}
```

Update `CloudSyncWorker.kt` and `PosViewModel.syncNow()` so they:

1. Check the provider master switch is on.
2. Check `ownerGmail` is set.
3. Request a Google sign‑in if no valid token exists.
4. Keep selling/backup unaffected if no Google account is connected.

### 2.5 Update owner UI

In `CloudBackupScreen.kt`:

- Add a **“Sign in with Google”** button (instead of only listing accounts via
  `AccountManager`).
- On success, call `viewModel.setOwnerGmail(email)` and store the token.
- The owner keeps:
  - “Backup now”
  - “Sync now”
  - “Change Google account”
- The owner must **not** see or be able to edit the provider master toggle.

### 2.6 Store secrets correctly

- Never store the OAuth client secret in the app.
- Store OAuth refresh tokens in `EncryptedSharedPreferences` or Android
  Keystore‑backed storage, not plain `SharedPreferences` and not Room.
- The provider access code should remain hashed with a salt (current
  `CloudSettingsRepository` already does SHA‑256).

### 2.7 Keep offline-first / no-breaking rules

- **No UI code path may await a network call.**
- A sale must complete without internet.
- Google sign‑in is optional.
- Sync is optional and default off.
- Turning sync off must leave a fully working app.
- Backup‑before‑sync must remain: create a rolling local backup first, then
  upload.
- Every changed file must pass `git diff --check` and brace balance.
- Do **not** rename the Room database or the package name. Existing installs
  keep their data.

---

## 3. Files you will change

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add credentials / play‑services‑auth / googleid versions |
| `app/build.gradle.kts` | Add the new dependencies |
| `.env.example` / secrets | Add `GOOGLE_OAUTH_CLIENT_ID` or similar build secret |
| `app/src/main/AndroidManifest.xml` | Add any required OAuth activity/redirect URIs (if using AppAuth) |
| `app/src/main/java/com/example/data/cloud/GoogleDriveAuthManager.kt` | New |
| `app/src/main/java/com/example/data/cloud/GoogleDriveCloudTransport.kt` | Use GoogleDriveAuthManager instead of AccountManager |
| `app/src/main/java/com/example/data/cloud/CloudSyncWorker.kt` | Use new auth path |
| `app/src/main/java/com/example/ui/viewmodel/PosViewModel.kt` | Add Google sign‑in handler, token storage, sign‑out |
| `app/src/main/java/com/example/ui/screens/more/CloudBackupScreen.kt` | Add “Sign in with Google” flow |
| `app/src/main/java/com/example/data/cloud/CloudSettings.kt` | Add optional token status fields |

---

## 4. Testing checklist

- [ ] Fresh install, no Google account pre‑connected → “Sign in with Google”
      works.
- [ ] Sign in succeeds → token stored in encrypted storage.
- [ ] Backup now saves a local rolling backup.
- [ ] Sync now creates a backup first, then uploads to the correct
      per‑device Drive folder.
- [ ] Hourly sync skips upload when the data hash has not changed.
- [ ] Daily backup runs without a Google account and does not try to upload.
- [ ] Owner cannot turn the master switch off; provider screen still requires
      access code + provider Gmail.
- [ ] Airplane mode: 100 sales complete; sync shows an error but selling never
      blocks.
- [ ] Revoking Google access: app keeps selling and shows a clear “connect your
      Google account again” state.
- [ ] Build from clean (`gradle :app:assembleDebug --no-daemon`) passes without
      a local `gradlew` or Java error in CI.

---

## 5. Acceptance criteria

1. Google Drive upload works on a fresh install when the owner signs in with
   their Gmail.
2. The provider can enable/disable cloud without the owner seeing it.
3. The owner sees only Backup now, Sync now, and the connected Google account.
4. Sync never overwrites without a backup created first.
5. Selling is fully offline and never waits on Google.
6. No third‑party barcode lookup is added.
7. Internal Room database name and package name stay unchanged.
8. No destructive migration is reintroduced.

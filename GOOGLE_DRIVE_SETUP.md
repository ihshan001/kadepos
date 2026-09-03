# Google Drive backup & multi-account setup

This document explains what the app now does, and — importantly — **what you
(the developer/owner) must configure yourself**, because Google only lets the
app talk to Drive after you set up a project in Google Cloud Console.

---

## 1. What the app now does

The cloud feature has been reworked around a **shared shop folder** and a
**multi-account model**:

- **One "main" Gmail (the hub).** The owner's main Google account is the single
  store of the whole shop's data on Google Drive.
- **Linked staff Gmails.** Each staff member (cashier, manager) can back up with
  their **own** Gmail. A cashier never needs the owner's password.
- **Shared folder.** Every device that belongs to the shop writes into one Drive
  folder: `arro-pos-<shopKey>` (the key is derived from the shop name, so it is
  stable across devices). Files are named `<device>__<timestamp>.zip`.
- **Sharing back to the hub.** When a staff phone uploads under its own account,
  the app also shares that file with the owner's main Gmail, so the owner sees
  every device's snapshots in one place.
- **Hour-by-hour record.** The app already synced hourly (only when data
  changed); the Backup & Cloud screen now shows a **"Hour-by-hour data"** list —
  the rolling local history plus a live listing of the shared Drive folder.
- **Staff Gmail field.** Each team member card now stores an optional Gmail, so
  linking their account is one tap.

Selling still works with no internet and never waits for the cloud; the backup
is a safety net only.

### Where to find it in the app

1. **More → Cloud & Backup** (only when the provider has switched the feature on,
   and only for an Owner/Manager).
   - **Main Google account** — set/change the owner's hub account.
   - **This phone's Google account** — which account this device syncs under.
   - **Linked team accounts** — link/unlink staff Gmails.
   - **Hour-by-hour data** — the snapshot history, with a Refresh button that
     reads the live Drive folder.
2. **More → My team → Edit a person** — add their Gmail (optional).

---

## 2. What YOU must do (Google Cloud Console)

The app calls the Google Drive REST API with an OAuth token obtained from the
account already signed in on the device (`AccountManager`, `drive.file` scope).
For Google to allow that, you must register the app once.

### Step 1 — Create a project

1. Go to <https://console.cloud.google.com>.
2. Create a new project (e.g. `arro-pos`). Note the **Project ID**.

### Step 2 — Enable the Drive API

1. In your project, open **APIs & Services → Library**.
2. Search **Google Drive API** and **Enable** it.

### Step 3 — Configure the OAuth consent screen

1. Open **APIs & Services → OAuth consent screen**.
2. User type: **External** (or Internal if this is a private team app).
3. Fill in the app name, support email, and developer contact email.
4. Scopes: the app only requests
   `https://www.googleapis.com/auth/drive.file` (per-file access to files the
   app creates). This is a **non-sensitive** scope, so it does not require
   Google verification.
5. Add yourself (and testers, if External) to the **Test users** list while
   testing.

### Step 4 — Create an Android OAuth client ID

This is the part that ties the APK to your Cloud project.

1. Open **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.aistudio.lkpos.wkyvbc`
   (this is the `applicationId` in `app/build.gradle.kts`).
4. **SHA-1 certificate fingerprint.** Generate it from your signing keystore:

   ```bash
   # Debug keystore (for local testing):
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
     -storepass android | grep "SHA1:"

   # Release keystore (the one used to sign the Play/installed APK):
   keytool -list -v -keystore /path/to/my-upload-key.jks -alias upload \
     -storepass "$STORE_PASSWORD" | grep "SHA1:"
   ```

   Add one credential per keystore you ship (debug + release). Google accepts
   multiple SHA-1 fingerprints on the same client ID.
5. Save. The generated **client ID / client secret** are for the Android client;
   because the app uses the system account chooser, there is nothing to paste
   into the APK — the match happens by package name + fingerprint.

> **Why this is needed:** without a matching Android OAuth client ID, the first
> "Connect Google account" / "Sync now" will fail with an invalid-client or
> consent error. There is no API key to embed — Drive auth is purely OAuth.

### Step 5 (optional, for Firebase)

The project already has the Firebase (`google-services`) plugin, but Drive
backup does **not** depend on Firebase. You can leave `google-services.json`
absent; the build is configured to warn, not fail, without it.

---

## 3. What the owner does on the phones

1. **Owner's phone:**
   - Add the owner's main Gmail to the phone (Android → Settings → Accounts).
   - More → Cloud & Backup → **Set main account** → choose the owner Gmail.
   - **Connect** the same account under "This phone's Google account".
   - Tap **Sync now** once; approve Drive access when Google asks.
2. **Each staff phone:**
   - Add the *staff member's own* Gmail to that phone.
   - Sign in the staff member (their PIN), then More → Cloud & Backup →
     connect **their** Gmail under "This phone's Google account".
   - On the owner's phone, **Link** that staff Gmail under "Linked team
     accounts" (or add it on the staff card under My team).
3. **Hour-by-hour view:** the owner opens More → Cloud & Backup → Hour-by-hour
   data → **Refresh** to see the snapshots arriving from every device.

### The provider switch

Cloud is gated behind a provider access code by design (the owner can't turn it
on alone). To activate for a shop: Settings → tap the "100% Offline-First" card
10 times, then long-press → **Provider access** → unlock with the provider
Gmail + access code → enable backup & cloud, hourly sync and daily backup.

---

## 4. Honest limitations (unchanged, still open)

- **Restore is not implemented.** Backups upload to Drive, but there is no
  in-app "restore onto a new phone" yet.
- **Backups are not encrypted.** They are a plain zip of the SQLite database,
  readable by whoever has the Drive account(s). For now treat Drive access as
  the security boundary.
- **`drive.file` scope.** Each account can only manage files it created. That is
  why a staff phone creates files under its own account and shares them back to
  the hub (they then appear in the owner's **"Shared with me"** in Drive, and in
  the shared folder the app lists). A staff account cannot overwrite the owner's
  files — which is also the point of the multi-account model.
- These paths have never been exercised on a real device (see `appinfo.md`);
  the Drive calls are written against the v3 REST API but should be smoke-tested
  with a real Google account before relying on them.

---

## 5. Files involved

- `app/src/main/java/com/example/data/cloud/CloudSettings.kt` — hub + linked
  accounts + shop key + rolling sync history.
- `app/src/main/java/com/example/data/cloud/CloudSettingsRepository.kt` —
  persistence + `shopKeyFor()`.
- `app/src/main/java/com/example/data/cloud/GoogleDriveCloudTransport.kt` —
  shared folder, file sharing, snapshot listing.
- `app/src/main/java/com/example/data/cloud/CloudSyncWorker.kt` — hourly sync
  into the shared folder, sharing back to the hub, history recording.
- `app/src/main/java/com/example/ui/screens/more/CloudBackupScreen.kt` — the
  owner-facing UI (hub, device account, linked accounts, hour-by-hour history).
- `app/src/main/java/com/example/data/model/Entities.kt` + `data/db/Migrations.kt`
  + `data/db/PosDatabase.kt` — staff `email` column (schema v7 → v8).

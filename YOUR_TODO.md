# Your To-Do List — Google Drive Backup

This is your checklist. Everything below is something **you** do. The app side
is already built; these are the steps that only you can complete.

---

## A. One-time Google Cloud setup

### 1. Create the project

1. Open <https://console.cloud.google.com> and sign in with **your** Google account.
2. Click the project picker at the top and choose **New Project**.
3. Name it (e.g. `arro-pos`) and click **Create**.
4. Note the project name — you'll come back to it.

### 2. Turn on the Drive API

1. In the left menu, open **APIs & Services → Library**.
2. Search for **Google Drive API**.
3. Click it, then click **Enable**.

### 3. Set up the consent screen

1. Open **APIs & Services → OAuth consent screen**.
2. If asked who will use the app, choose **External**, then **Create**.
3. Fill in:
   - **App name** — e.g. `Arro-POS`
   - **User support email** — your email
   - **Developer contact email** — your email
4. Click through the scopes page (you do not need to add scopes here).
5. On **Test users**, add your own email address, then **Save**.

### 4. Create the Android client ID

1. Open **APIs & Services → Credentials**.
2. Click **Create credentials → OAuth client ID**.
3. **Application type**: `Android`.
4. **Name**: `Arro-POS Android`.
5. **Package name**: `com.aistudio.lkpos.wkyvbc`
6. **SHA-1 certificate fingerprint** — you must generate this from your
   computer. See the next section.
7. Click **Create**, then **Done**.

---

## B. Get your SHA-1 fingerprint (do this on your computer)

Open a terminal where your signing keystore is, and run **one** of these:

```bash
# If testing with the debug build:
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android | grep "SHA1:"
```

```bash
# For the release APK you will actually ship:
keytool -list -v -keystore my-upload-key.jks \
  -alias upload -storepass "YOUR_STORE_PASSWORD" | grep "SHA1:"
```

Copy the `SHA1:` value and paste it into the **SHA-1 certificate fingerprint**
box from step 4 above.

> If you ship both a test build and a real build, add **both** fingerprints.
> You can add more than one to the same client ID.

---

## C. On the phones (after the Cloud part is done)

### Owner's phone

1. Go to Android **Settings → Accounts → Add account → Google**, and sign in
   with the **owner's main Gmail**.
2. Open Arro-POS → **More → Cloud & Backup**.
3. Tap **Set main account** and choose the owner Gmail.
4. Tap **Connect Google account** (under "This phone's Google account") and
   choose the same account.
5. Tap **Sync now**. Approve the Google permission screen when it appears.

### Each staff phone

1. Add **that staff member's own Gmail** to the phone
   (Settings → Accounts → Add account → Google).
2. Open Arro-POS, sign in as that staff member with their PIN.
3. **More → Cloud & Backup → Connect Google account**, choose **their** Gmail.

### Back on the owner's phone

1. **More → Cloud & Backup → Linked team accounts → Link**, and add each staff
   member's Gmail (or add it on their card under **More → My team → Edit**).
2. Open **Hour-by-hour data → Refresh** to see every phone's snapshots.

---

## D. Turn the feature on for a shop (provider only)

Cloud is locked behind a provider code. To switch it on for a shop:

1. **Settings →** tap the **"100% Offline-First"** card **10 times**, then
   press and hold it.
2. **Provider access** → enter the **provider Gmail** and **access code**, then
   **Unlock**.
3. Turn on **Enable backup & cloud**, **Hourly sync**, and **Daily backup**,
   then **Save**.

---

## Quick answers

- **Do I need to paste an API key into the app?** No. Drive uses OAuth; Google
  matches the app by package name + SHA-1 fingerprint.
- **What scope does it use?** `drive.file` only — it can only touch files the
  app itself creates. It does not need Google verification.
- **Where does the data go?** One Drive folder named
  `arro-pos-<shop name>`, shared across the shop's phones.

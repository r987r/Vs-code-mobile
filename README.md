# VS Code Mobile — GitHub Codespaces on Android

[![Android CI](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml/badge.svg)](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml)

A native Android app that opens your GitHub Codespaces in a mobile-optimised view,
with a VS Code-style left Activity Bar for quick panel switching and a toggleable
Mobile / Desktop view mode.

---

## ⚠️ One-time setup required before sign-in works

The app uses **GitHub OAuth** to authenticate users.  Before anyone can tap
"Sign in with GitHub" and actually log in, the **repository owner** must:

### Step 1 — Register a GitHub OAuth App

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
   (direct link: <https://github.com/settings/developers>).
2. Fill in the form:
   | Field | Value |
   |---|---|
   | Application name | `VS Code Mobile` (or any name you like) |
   | Homepage URL | `https://github.com/YOUR_USERNAME/Vs-code-mobile` (or your fork URL) |
   | Authorization callback URL | **`com.vscode.mobile://oauth2redirect`** (exact value, no trailing slash) |
3. Click **Register application**.
4. On the next page, copy your **Client ID**.
5. Click **Generate a new client secret** and copy the secret immediately
   (GitHub shows it only once).

### Step 2 — Add the credentials as repository secrets

1. In this repository, go to **Settings → Secrets and variables → Actions**.
2. Add two new **Repository secrets**:
   | Secret name | Value |
   |---|---|
   | `GH_CLIENT_ID` | The Client ID from Step 1 |
   | `GH_CLIENT_SECRET` | The Client Secret from Step 1 |
3. **Push a commit** (or re-run the CI workflow) so the APK is rebuilt with the
   real credentials baked in via `BuildConfig`.

> **Why is this needed?**
> The OAuth client ID is embedded in the app at build time.  The CI workflow
> reads these secrets and injects them into the build.  Without real credentials,
> the build uses a placeholder and sign-in will show an error message instead of
> opening the GitHub login page.

> **Note:** In a production app you would proxy the token exchange through your
> own backend so the client secret is never shipped in the APK.  See the
> [Security design](#security-design) section for details.

---

## Quick start — download & sideload the app

Every push to `main` automatically runs **secret scanning, Android Lint, unit tests,
and a build** before producing a ready-to-install debug APK — no Android Studio or SDK
required on your end.

> **Prerequisite:** The PR must be **merged into `main`** (or you push directly to
> `main`) before the APK is generated. Once merged, the workflow runs automatically.

### Step-by-step: get the APK onto your phone

**Step 1 — Merge this PR (or push to `main`)**
The Android CI workflow starts automatically within seconds.

**Step 2 — Wait for the build to finish (~5 min)**
Watch the green checkmark appear on the
[Actions tab](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml).
All five checks must pass: Secret Scan → Lint → Tests → Build → ✅

**Step 3 — Download the APK**
1. Click the most recent successful **"Android CI"** workflow run.
2. Scroll to **Artifacts** at the bottom of the page.
3. Download **`VSCodeMobile-debug`** (a `.zip` file).
4. Unzip it — you will find `app-debug.apk` and `app-debug.apk.sha256` inside.

**Step 4 — (Recommended) Verify the APK has not been tampered with**
On your computer, open a terminal in the folder where you unzipped the archive:
```bash
sha256sum -c app-debug.apk.sha256
# Expected output: app-debug.apk: OK
```
You can also compare the hash shown in the workflow run's **Summary** panel.

**Step 5 — Enable "Install unknown apps" on your Android phone**

> **Requires Android 8.0 (API 26) or later.**

- **Android 8–9:** Settings → Apps → ⋮ → Special app access → Install unknown apps  
  → select your file manager → turn on **Allow from this source**
- **Android 10+:** Settings → Apps → (select your file manager) → Install unknown apps → Allow

**Step 6 — Transfer and install the APK**
1. Send `app-debug.apk` to your phone (USB cable, Google Drive, email — any method).
2. Open the file on your phone; tap **Install**.
3. Launch **VS Code Mobile** and tap **Sign in with GitHub**.

### Tagged releases (optional)

Push a Git tag to automatically create a
[GitHub Release](https://github.com/r987r/Vs-code-mobile/releases)
with the APK and its checksum attached as release assets:
```bash
git tag v1.0.0 && git push --tags
```

## CI pipeline — what protects you

Every push and pull request runs these checks **in order**; the APK is only produced
if every check passes:

| Job | Tool | What it catches |
|-----|------|----------------|
| 🔍 Secret Scanning | TruffleHog | Accidentally committed tokens, API keys, passwords |
| 🧹 Android Lint | AGP `lintDebug` | Insecure WebView settings, hardcoded credentials, 500+ code & security rules |
| 🧪 Unit Tests | JUnit / Mockito | Regressions in auth, API client, WebView logic |
| 🔒 Dependency Review *(PRs only)* | GitHub Dependency Review | New dependencies with known CVEs (blocks `high` severity) |
| 📦 Build & Checksum | Gradle + `sha256sum` | Reproducible APK with integrity hash |

The APK artifact also ships with a **SHA-256 checksum file** so you can verify the
download hasn't been altered before installing it on your phone.

---

## Features

| Feature | Details |
|---|---|
| 🔐 Secure sign-in | GitHub OAuth 2 + PKCE via [AppAuth](https://github.com/openid/AppAuth-Android) |
| 🗝️ Keystore-backed token storage | `EncryptedSharedPreferences` (AES-256-GCM) |
| 📱 Mobile-friendly layout | JavaScript injection resizes VS Code panels, enlarges touch targets |
| 🖥️ Desktop mode toggle | Switch between mobile-optimised and full desktop layout on the fly |
| 📂 Left Activity Bar | Explorer · Search · Source Control · Extensions · Copilot · Terminal |
| 🔒 Strict security | Certificate pinning, HTTPS-only, allowlisted domains, no SSL bypasses |
| 🚫 No data leaks | Backups disabled, third-party cookies blocked, no cleartext traffic |

---

## Architecture

```
app/src/main/java/com/vscode/mobile/
├── auth/
│   ├── GitHubAuthManager.kt      # OAuth2 + PKCE flow via AppAuth
│   └── SecureTokenStorage.kt     # Android Keystore-backed token storage
├── model/
│   ├── Codespace.kt              # Data classes for GitHub API responses
│   └── ViewMode.kt               # ViewMode & CodespacePanel enums
├── ui/
│   ├── SplashActivity.kt         # Launch screen / routing
│   ├── LoginActivity.kt          # GitHub sign-in screen
│   ├── CodespacesListActivity.kt # List of user's codespaces
│   ├── CodespacesAdapter.kt      # RecyclerView adapter
│   └── MainActivity.kt           # Codespace WebView + Activity Bar tabs
├── util/
│   └── GitHubApiClient.kt        # Lightweight GitHub REST API v3 client
└── webview/
    ├── CodespacesWebViewClient.kt # Domain allowlist, SSL enforcement
    └── JavaScriptInjector.kt      # Mobile CSS/JS injection into VS Code web
```

---

## Security design

1. **OAuth2 + PKCE** — no passwords or personal access tokens ever touch the app.
2. **Keystore-backed storage** — tokens are encrypted at rest with AES-256-GCM via Android Keystore.
3. **Domain allowlist** — `CodespacesWebViewClient` only permits navigation to `github.com`, `github.dev`, `app.github.dev`, and `githubpreview.dev`. Anything else is opened in the system browser.
4. **SSL errors blocked** — `onReceivedSslError` always calls `handler.cancel()`, never `handler.proceed()`.
5. **No cleartext traffic** — `android:usesCleartextTraffic="false"` and `network_security_config.xml` enforces HTTPS everywhere.
6. **Certificate pinning** — `network_security_config.xml` pins DigiCert roots for `github.com` / `api.github.com`.
7. **No backups** — `data_extraction_rules.xml` and `backup_rules.xml` exclude all app data from cloud/device backups.
8. **Third-party cookies blocked** — `CookieManager.setAcceptThirdPartyCookies(webView, false)`.
9. **Remote debugging disabled in release** — `WebView.setWebContentsDebuggingEnabled(true)` only in debug builds.

---

## Building

### Prerequisites

* Android Studio Hedgehog (2023.1.1) or later
* Android SDK 34
* JDK 17

### 1. Register a GitHub OAuth App

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**.
2. Set **Authorization callback URL** to `com.vscode.mobile://oauth2redirect`.
3. Note your **Client ID** and **Client Secret**.

### 2. Configure credentials

Copy `local.properties.example` to `local.properties` in the project root
(this file is git-ignored — **never commit it**):

```bash
cp local.properties.example local.properties
# then edit local.properties and fill in your SDK path and OAuth credentials
```

```properties
sdk.dir=/path/to/android-sdk
GH_CLIENT_ID=YOUR_GITHUB_CLIENT_ID_HERE
GH_CLIENT_SECRET=YOUR_GITHUB_CLIENT_SECRET_HERE
```

> **Note:** In production, proxy the token exchange through your own backend to avoid
> shipping the client secret in the APK.

### 3. Build & run

```bash
./gradlew assembleDebug
# or open in Android Studio and press Run
```

### 4. Run unit tests

```bash
./gradlew test
```

### 5. Configure GitHub Secrets for CI (optional)

The Actions workflow builds the APK automatically on every push. To include real
OAuth credentials in the CI build, add the following
[repository secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions):

| Secret name           | Value                          |
|-----------------------|--------------------------------|
| `GH_CLIENT_ID`    | Your GitHub OAuth App client ID |
| `GH_CLIENT_SECRET`| Your GitHub OAuth App secret   |

If these secrets are absent, the workflow still builds a placeholder APK (the
`REPLACE_WITH_YOUR_CLIENT_ID` build-time default is used), which is useful for
verifying that the project compiles correctly.

---

## Usage

1. Launch the app → tap **Sign in with GitHub**.
2. Authorise the app in the browser.
3. Your codespaces are listed — tap **Open** on any running codespace.
4. Use the **left Activity Bar** to switch between Explorer, Search, Source Control, Extensions, Copilot Chat, and Terminal.
5. Tap the **⋮ menu → View Mode** to toggle between **Mobile** (touch-optimised panels) and **Desktop** (full VS Code layout).

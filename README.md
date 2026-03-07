# VS Code Mobile — GitHub Codespaces on Android

[![Android CI](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml/badge.svg)](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml)

A native Android app that opens your GitHub Codespaces in a mobile-optimised view,
with a VS Code-style left Activity Bar for quick panel switching and a toggleable
Mobile / Desktop view mode.

---

## Quick start — download & sideload the app

Every push to `main` triggers a GitHub Actions build that produces a ready-to-install
debug APK — **no Android Studio or SDK required**.

### Download the latest debug APK

1. Go to the [**Actions** tab](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml)
   of this repository.
2. Click the most recent successful **"Android CI"** workflow run.
3. Scroll to **Artifacts** at the bottom of the page and download **`VSCodeMobile-debug`**.
4. Unzip the downloaded archive — the `.apk` file is inside.

### Install the APK on your Android phone

> **Requires Android 8.0 (API 26) or later.**

1. **Enable "Install unknown apps"** on your device:
   - Android 8+: **Settings → Apps → Special app access → Install unknown apps**,
     then allow your file manager or browser.
2. Transfer the `.apk` to your phone (USB, Google Drive, email, etc.).
3. Open the `.apk` on your phone and tap **Install**.
4. Launch **VS Code Mobile** and sign in with your GitHub account.

### Tagged releases

Push a Git tag (e.g. `git tag v1.0.0 && git push --tags`) to automatically
create a [GitHub Release](https://github.com/r987r/Vs-code-mobile/releases)
with the release APK attached.

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
GITHUB_CLIENT_ID=YOUR_GITHUB_CLIENT_ID_HERE
GITHUB_CLIENT_SECRET=YOUR_GITHUB_CLIENT_SECRET_HERE
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
| `GITHUB_CLIENT_ID`    | Your GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET`| Your GitHub OAuth App secret   |

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

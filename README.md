# VS Code Mobile — GitHub Codespaces on Android

[![Android CI](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml/badge.svg)](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml)

A native Android app that opens your GitHub Codespaces in a mobile-optimised view,
with a VS Code-style left Activity Bar for quick panel switching, a toggleable
Mobile / Desktop view mode, and a FAB toggle to switch between the codespace list
and your active codespace.

---

## ✅ No credentials or OAuth setup required

The app uses GitHub's **built-in web sign-in** — just open the app, sign in to
GitHub through the WebView, and your codespaces appear.  No OAuth App registration,
no client IDs, no secrets.  The WebView retains cookies so you stay signed in
between sessions.

---

## Quick start — download & sideload the app

Every push to `main` automatically runs **secret scanning, Android Lint, unit tests,
and a build** before producing a ready-to-install debug APK — no Android Studio or SDK
required on your end.

### Step-by-step: get the APK onto your phone

**Step 1 — Merge this PR (or push to `main`)**
The Android CI workflow starts automatically within seconds.

**Step 2 — Wait for the build to finish (~5 min)**
Watch the green checkmark appear on the
[Actions tab](https://github.com/r987r/Vs-code-mobile/actions/workflows/android-build.yml).
All checks must pass: Secret Scan → Lint → Tests → Build → ✅

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

**Step 5 — Enable "Install unknown apps" on your Android phone**

> **Requires Android 8.0 (API 26) or later.**

- **Android 8–9:** Settings → Apps → ⋮ → Special app access → Install unknown apps  
  → select your file manager → turn on **Allow from this source**
- **Android 10+:** Settings → Apps → (select your file manager) → Install unknown apps → Allow

**Step 6 — Transfer and install the APK**
1. Send `app-debug.apk` to your phone (USB cable, Google Drive, email — any method).
2. Open the file on your phone; tap **Install**.
3. Launch **VS Code Mobile** — sign in to GitHub in the browser and start coding.

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
| 🧪 Unit Tests | JUnit / Mockito | Regressions in WebView logic, URL detection |
| 🔒 Dependency Review *(PRs only)* | GitHub Dependency Review | New dependencies with known CVEs (blocks `high` severity) |
| 📦 Build & Checksum | Gradle + `sha256sum` | Reproducible APK with integrity hash |

---

## Features

| Feature | Details |
|---|---|
| 🌐 Browser-based sign-in | Sign in via GitHub's web UI in the WebView — no OAuth setup |
| 🍪 Persistent sessions | Cookies are retained so you stay signed in between launches |
| 📱 Mobile-friendly layout | JavaScript injection resizes VS Code panels, enlarges touch targets |
| 🖥️ Desktop mode toggle | Switch between mobile-optimised and full desktop layout on the fly |
| 🔄 Toggle FAB | Quickly switch between codespace list and active codespace |
| 📂 Left Activity Bar | Explorer · Search · Source Control · Extensions · Copilot · Terminal |
| 🔒 Strict security | Certificate pinning, HTTPS-only, allowlisted domains, no SSL bypasses |
| 🚫 No data leaks | Backups disabled, third-party cookies blocked, no cleartext traffic |

---

## Architecture

```
app/src/main/java/com/vscode/mobile/
├── model/
│   └── ViewMode.kt               # ViewMode & CodespacePanel enums
├── ui/
│   ├── SplashActivity.kt         # Launch screen → routes to MainActivity
│   └── MainActivity.kt           # Single-activity WebView wrapper:
│                                  #   - Loads github.com/codespaces
│                                  #   - User signs in via web
│                                  #   - Detects codespace URLs
│                                  #   - Toggle FAB for list ↔ codespace
│                                  #   - Activity Bar + view mode toggle
└── webview/
    ├── CodespacesWebViewClient.kt # Domain allowlist, SSL enforcement
    └── JavaScriptInjector.kt      # Mobile CSS/JS injection into VS Code web
```

---

## Security design

1. **No credentials in the app** — the app never stores OAuth tokens, client IDs, or secrets. Authentication is handled entirely by GitHub's web login in the WebView.
2. **Cookie-based sessions** — the WebView persists first-party cookies for GitHub. Users can clear their session at any time via the menu.
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

### Build & run

```bash
./gradlew assembleDebug
# or open in Android Studio and press Run
```

### Run unit tests

```bash
./gradlew test
```

---

## Usage

1. Launch the app → GitHub's sign-in page appears in the WebView.
2. Sign in with your GitHub account.
3. Your codespaces list loads — tap any codespace to open it.
4. Use the **left Activity Bar** to switch between Explorer, Search, Source Control, Extensions, Copilot Chat, and Terminal.
5. Tap the **floating toggle button** to switch between the codespace list and your active codespace.
6. Tap the **⋮ menu → View Mode** to toggle between **Mobile** (touch-optimised panels) and **Desktop** (full VS Code layout).
7. Use **⋮ menu → Clear session & sign out** to clear cookies and sign out.

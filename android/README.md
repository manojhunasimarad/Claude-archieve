# Android apps

One small Android shell that turns each web app into an installable app. It lives in this private repo because it holds the signing key; the app files come from the public repos at build time.

| App | Package | Bundled from |
| --- | --- | --- |
| LLM From Zero | `io.github.manojhunasimarad.llmfromzero` | `llm-from-zero/` in [manojhunasimarad.github.io](https://github.com/manojhunasimarad/manojhunasimarad.github.io) |
| Kitchen | `io.github.manojhunasimarad.kitchen` | the [Kitchen](https://github.com/manojhunasimarad/Kitchen) repo |
| Dalal Dojo | `io.github.manojhunasimarad.dalaldojo` | `dalal-dojo/` in the website repo |
| Dojo Lessons | `io.github.manojhunasimarad.dalaldojo.lessons` | `dalal-dojo-lessons/` in the website repo |

## Install on your phone

1. On the phone, sign in to GitHub in the browser and open **[the latest release](https://github.com/manojhunasimarad/Claude-archieve/releases/latest)** (the repo is private, so it needs your sign-in).
2. Tap the `.apk` you want. When Android asks, allow your browser to install apps.
3. Later builds install over the old one and keep your data, because every build is signed with the same key.

Your progress lives inside each app on the phone. It is separate from the website's progress in Chrome; use each app's own export/import or QR sync to move it.

## How it works

- Each APK carries its web app's files. A tiny server inside the app (`AssetServer.java`) serves them on `http://127.0.0.1:<port>`, reachable only from the phone itself. That makes web workers, WebAssembly and offline caching behave exactly as in Chrome.
- Links to other sites open in the phone's browser.
- File pickers work for imports. Exports ("download" links) open a "Save as" screen (`res/raw/download_hook.js`).
- LLM From Zero may use the camera for QR sync. It asks the first time.
- LLM From Zero downloads Python (Pyodide) from a CDN the first time you run code, so do that once online; after that it is cached for offline use.

## Build

GitHub builds everything on each push that touches `android/` (workflow: `.github/workflows/android.yml`) and publishes a release with four `.apk` files (install) and four `.aab` files (Google Play). After changing an app on the website or in the Kitchen repo, rebuild by hand: Actions tab → Android apps → Run workflow.

To build on a laptop: install Android Studio, then

```bash
bash android/sync-web.sh ../manojhunasimarad.github.io ../Kitchen   # copy the web apps in
cd android && ./gradlew assembleRelease   # APKs in app/build/outputs/apk/<app>/release/
```

## Signing and Google Play

`sideload.keystore` is committed on purpose (this repo is private) so every build has the same signature and updates keep your data. Keep this repo private; if it ever becomes public, make a new key, since anyone holding it could sign an "update" for these apps.

For Google Play, create a private upload key that never goes into a repo:

```bash
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then sign the `.aab` with it (or add it as GitHub secrets and change the `signingConfigs` block). Play requirements as of Nov 2026: target API level 36 (this project already does), an app bundle (`.aab`), a privacy policy URL, and, for personal developer accounts made after Nov 13, 2023, a closed test with at least 12 testers for 14 days before going public.

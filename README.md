<div align="center">

<img src="docs/assets/readme/hermex-icon.png" alt="Hermex app icon" width="96" />

# Hermex for Android

**A native Android client for controlling your self-hosted [Hermes](https://github.com/nesquena/hermes-webui) agent.**

Your server. Your phone. No middleman.

[![Latest release](https://img.shields.io/github/v/release/Hungbocluaqua/hermex-android-port?label=release&color=2F6B4F)](https://github.com/Hungbocluaqua/hermex-android-port/releases/latest)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white)](android/README.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](LICENSE)

[![Download APK](https://img.shields.io/badge/Download-signed%20APK-2F6B4F?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Hungbocluaqua/hermex-android-port/releases/latest/download/app-release.apk)

[Latest release](https://github.com/Hungbocluaqua/hermex-android-port/releases/latest) · [Android source](android/) · [Report a bug](https://github.com/Hungbocluaqua/hermex-android-port/issues) · [Contributing](CONTRIBUTING.md)

</div>

Hermex is a native Kotlin and Jetpack Compose port of the original SwiftUI app. It turns an Android phone into a mobile cockpit for a [hermes-webui](https://github.com/nesquena/hermes-webui) server running on hardware you control. The phone handles interaction; your server continues to run the agent, tools, models, and workspaces.

- **Free and open source.** No subscriptions or in-app purchases.
- **Private by design.** No analytics, tracking, hosted account, or third-party relay.
- **Actually native.** Compose UI, Android notifications, share targets, shortcuts, widgets, encrypted local storage, and no WebView wrapper.
- **Offline aware.** Recent sessions and messages remain available from the Room cache when the server cannot be reached.

The original iPhone app is still maintained in this repository, but this README focuses on the Android port and its releases.

## Install Hermex

Hermex is currently distributed as a signed APK through GitHub Releases and requires Android 8.0 (API 26) or newer.

1. Download the latest signed [`app-release.apk`](https://github.com/Hungbocluaqua/hermex-android-port/releases/latest/download/app-release.apk) on your Android device.
2. If prompted, allow your browser or file manager to install apps from that source.
3. Open the APK and install Hermex. Future signed releases can be installed over the existing app.
4. Enter your `hermes-webui` server URL and password during onboarding.

The matching AAB and version-specific notes are available on the [latest release page](https://github.com/Hungbocluaqua/hermex-android-port/releases/latest) for store distribution and release verification; Android users should install the APK instead.

## Android features

### Chat and sessions

- Stream responses over SSE with thinking, tool-call, approval, and clarification detail.
- Send text, images, files, voice notes, model choices, profiles, reasoning effort, and workspace context.
- Stop, steer, interrupt, retry, undo, compress context, and use supported slash commands during a run.
- Browse, search, pin, archive, restore, rename, delete, move, and branch server sessions.
- Organize sessions with projects and keep recent transcripts readable offline.

### Agent controls

- Browse workspace directories and preview text, images, and binary file metadata.
- Inspect Git status and diffs; stage, unstage, commit, fetch, pull, push, and switch branches with confirmations around destructive actions.
- Create, edit, run, pause, inspect, and delete scheduled tasks.
- Browse and toggle skills, edit memory sections, and review usage insights.
- Switch models, providers, profiles, reasoning levels, and the server's default model.

### Android integration

- Receive active-stream status notifications, including Android 13+ notification permission handling.
- Share text and files into a new Hermex session from other Android apps.
- Record voice notes, transcribe them through the server, and send the result with its audio attachment.
- Launch sessions, new chats, and voice sessions from static or dynamic app shortcuts.
- Add the Hermex home-screen widget for quick access to sessions and new chats.
- Play assistant responses using server TTS with Android TextToSpeech as a fallback.
- Store server profiles, cookies, headers, and credentials using Android encrypted storage and Keystore-backed protection.
- Use system, light, or dark appearance and any of the app's 18 bundled locales.

## Connect to your server

Hermex is a client only. It does not include, host, or provision a backend. You need a working [hermes-webui](https://github.com/nesquena/hermes-webui) server on macOS, Linux, or Windows/WSL2 with `HERMES_WEBUI_PASSWORD` set.

1. Start `hermes-webui` and confirm that `http://localhost:8787/health` works on the server machine.
2. Make the server reachable from your Android device with HTTPS, Tailscale, or a trusted private network.
3. Enter the reachable server URL and password in Hermex.

Self-hosting, securing, updating, and keeping the server online remain your responsibility.

### HTTPS tunnel or reverse proxy

This is the recommended option when you need access away from home. Expose `hermes-webui` through Cloudflare Tunnel or another reverse proxy that terminates real TLS at a hostname you control, then connect Hermex to a URL such as:

```text
https://hermes.yourdomain.com
```

Because the hostname is publicly reachable, use a strong `HERMES_WEBUI_PASSWORD`. Consider an additional access policy if your proxy supports one.

### Tailscale

Tailscale Serve provides private HTTPS without exposing `hermes-webui` on every network interface.

1. Install Tailscale on the server and Android device and sign both into the same tailnet.
2. Keep the authenticated server bound to `127.0.0.1:8787` and verify `http://127.0.0.1:8787/health` locally.
3. Inspect `tailscale serve status` and `tailscale funnel status`. Preserve every existing route and never enable Funnel for this setup.
4. Only when HTTPS port 443 at the root path is unused, run `tailscale serve --bg 8787`.
5. Read back `tailscale serve status`, verify `https://<actual-ts.net-hostname>/health`, and enter that exact HTTPS URL in Hermex.

Do not reset or overwrite an occupied Serve route. Binding to `0.0.0.0` or connecting to a Tailscale IP over plain HTTP is an explicit manual fallback with broader exposure, not the default setup.

### Connection troubleshooting

If onboarding cannot reach the server, verify that:

1. The server machine is awake and `hermes-webui` is running.
2. `/health` responds from another device on the same route.
3. The tunnel, reverse proxy, local network, or Tailscale connection is active.
4. The URL includes the correct scheme and port, and the password matches `HERMES_WEBUI_PASSWORD`.

## Build the Android app

The Android project lives in [`android/`](android/README.md). It targets API 36, supports API 26+, uses Java 17, Kotlin, Jetpack Compose, Material 3, Room, DataStore, OkHttp, and SSE.

Install JDK 17 and Android SDK Platform 36, then run from PowerShell:

```powershell
cd android
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

For broader validation:

```powershell
./gradlew.bat connectedDebugAndroidTest
./gradlew.bat lintDebug
./gradlew.bat lintRelease
./gradlew.bat assembleRelease
./gradlew.bat bundleRelease
```

Build outputs:

- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Unsigned local release APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`
- Release bundle: `android/app/build/outputs/bundle/release/app-release.aab`

Local release builds are unsigned unless the signing environment variables described in [`android/RELEASE.md`](android/RELEASE.md) are configured. A signed build uses `app-release.apk`. Maintainers use the `Android CI` workflow for validation and `Android Named Release` to publish production-signed APK and AAB artifacts. See [`android/PLAY_RELEASE_CHECKLIST.md`](android/PLAY_RELEASE_CHECKLIST.md) for the release gate.

## iOS version

Hermex began as a native SwiftUI iPhone app and remains available on the [App Store](https://apps.apple.com/app/hermex/id6767006319). Its source is under [`HermesMobile/`](HermesMobile/), with Xcode target and scheme `HermesMobile`.

The Android port uses the same server contract and product model, but it is an independent native implementation rather than a cross-platform wrapper. iOS development and TestFlight instructions remain in [`DEVELOPMENT.md`](DEVELOPMENT.md) and [`TESTFLIGHT.md`](TESTFLIGHT.md).

## Server compatibility

Hermex is developed and tested against the `hermes-webui` commit pinned in [`UPSTREAM_TESTED_SHA`](UPSTREAM_TESTED_SHA). Upstream does not yet guarantee API stability, so newer or older server versions can break individual features. Include your server version when reporting a bug.

The Android client uses tolerant JSON decoding and verifies endpoint shapes against upstream source instead of inventing API contracts. See [`CONTRACT_TESTS.md`](CONTRACT_TESTS.md) for the compatibility and pin-advance process.

## Project map

- [`android/`](android/README.md): native Android source, tests, Gradle configuration, Fastlane metadata, and release docs.
- [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml): Android build, test, and lint validation.
- [`.github/workflows/android-named-release.yml`](.github/workflows/android-named-release.yml): signed APK/AAB GitHub release workflow.
- [`PROJECT_SPEC.md`](PROJECT_SPEC.md): original product and API source of truth.
- [`DEVELOPMENT.md`](DEVELOPMENT.md): local development and maintainer workflows.
- [`CONTRACT_TESTS.md`](CONTRACT_TESTS.md): upstream contract-test readiness and pin policy.
- [`SECURITY.md`](SECURITY.md): vulnerability reporting.

## Contributing

Contributions are welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md), the repository working agreement in [`AGENTS.md`](AGENTS.md), and the [Code of Conduct](CODE_OF_CONDUCT.md) before opening a pull request.

- Never invent API endpoints or JSON shapes; verify them against a running server, the official API docs, or pinned upstream source.
- Decode upstream responses tolerantly and ignore unknown fields safely.
- Do not add third-party dependencies without approval.
- Do not modify the upstream `hermes-webui` server from this repository.

## License

MIT — see [LICENSE](LICENSE).

Hermex is an independent client and is not affiliated with the upstream [hermes-webui](https://github.com/nesquena/hermes-webui) project. Android, Jetpack, and Google Play are trademarks of Google LLC. Apple and App Store are trademarks of Apple Inc.

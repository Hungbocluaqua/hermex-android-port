# Hermex Android

Native Android port of Hermex. This project intentionally mirrors the iOS app's
contract-first shape: the server remains `hermes-webui`, and Android talks to the
existing REST + SSE API.

## Build

Install Android SDK platform 36, then run:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat assembleDebugAndroidTest
./gradlew.bat bundleRelease
```

On Windows, a malformed quoted entry in `PATH` can break Gradle's forked test
JVM with an error like `Could not find or load main class Files\nodejs`. If that
happens, rerun with a sanitized process PATH:

```powershell
$env:Path=(($env:Path -split ';') | Where-Object { $_ -and $_ -notmatch '"' } | Select-Object -Unique) -join ';'
```

## Current Scope

Implemented foundation:

- Kotlin + Jetpack Compose Android app under `android/`.
- Endpoint matrix and API client for the existing Hermex/Hermes WebUI contract.
- OkHttp cookies, SSE, multipart upload, and sanitized custom headers.
- Android encrypted storage for server registry/custom headers/cookies.
- Room offline cache for sessions/messages.
- Compose onboarding, session list, chat, panels, and settings routes.
- Session list search, archived-session view, project filters, project
  create/rename/delete, and session pin/archive/restore/rename/delete/move/
  branch actions.
- Session-scoped workspace browser with directory navigation and text preview.
- Session-scoped git screen with status, diff, stage/unstage, fetch/pull/push,
  branch checkout/create, generated commit-message, destructive confirmations,
  and commit flows.
- Composer model/profile/reasoning chips plus Android document-picker uploads.
- Chat-time approval and clarification prompt cards wired to the upstream
  pending/respond endpoints.
- Chat slash commands and action controls for help/clear/stop/steer/interrupt,
  goals, context compression, undo, retry, model/profile/reasoning switches,
  and local status.
- Message copy actions and assistant response playback through server TTS audio,
  with Android TextToSpeech as a fallback.
- Active chat stream status notifications with Android 13 notification
  permission handling.
- Markdown transcript rendering through Markwon, including tables, task lists,
  strikethrough, inline HTML, linkified text, themed code blocks, and Prism4j
  syntax highlighting for common fenced-code languages plus LaTeX math rendering.
- Static Android launcher shortcuts for sessions, new session, panels, and
  settings, backed by Compose deep links.
- Android home-screen widget with quick actions for sessions and new session.
- Android share target intake for text and shared files: incoming files are
  copied into app cache, a fresh session is opened, shared text lands in the
  composer, and shared files upload as pending attachments.
- Voice-note recording to a local cache file and server transcription through
  `POST /api/transcribe`, with the transcript inserted back into the draft.
- Workspace text, raw image, and binary metadata previews.
- Panels for insights, scheduled task create/edit/run/pause/output/delete,
  skill detail/toggle, and editable memory sections.
- Settings default-model picker backed by `/api/models` and `/api/default-model`.
- Local app appearance setting for system/light/dark theme, stored in DataStore.
- Starter Fastlane-compatible Play metadata for internal testing under
  `fastlane/metadata/android/en-US`.
- Optional environment-based release signing config plus Fastlane lanes for
  local release-bundle validation and draft Play internal-track upload.
- Unit tests for endpoint shape, tolerant JSON decoding, SSE mapping, and header sanitization.
  Additional tests cover approval/clarification respond request keys, git
  checkout/TTS bodies, goal/compress bodies, panel/session mutation bodies, and
  rich git/cron/goal decoding, shortcut/share action contracts, and shared-draft
  serialization.
- Compose instrumentation tests for app launch/root-surface rendering, onboarding
  connection flow against MockWebServer, and session-list rendering from
  MockWebServer sessions/projects. Additional chat instrumentation coverage
  drives composer send through `POST /api/chat/start` and an SSE response stream.

Known follow-up:

- Replace the temporary AGP 9 legacy Kotlin/KAPT flags once Room supports the
  built-in Kotlin path cleanly in this dependency set.
- Run device UI coverage for chat/git/share paths, add production release
  signing secrets, and capture Play screenshots.

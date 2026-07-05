# Android Release

The Android project can build unsigned local release bundles with:

```powershell
./gradlew.bat bundleRelease
```

For a signed Play internal-test bundle, provide signing and Play credentials
through environment variables. Do not commit keystores or service-account JSON.

Required signing variables:

- `HERMEX_ANDROID_KEYSTORE_FILE`
- `HERMEX_ANDROID_KEYSTORE_PASSWORD`
- `HERMEX_ANDROID_KEY_ALIAS`
- `HERMEX_ANDROID_KEY_PASSWORD`

Required Play upload variable:

- `SUPPLY_JSON_KEY`

Fastlane commands:

```powershell
bundle exec fastlane android build_release
bundle exec fastlane android internal
```

`android internal` uploads the release bundle to the Play internal track as a
draft and skips screenshots/images until production-ready assets exist.

# Hermex Android Play Metadata

This folder contains Fastlane-compatible Play Store metadata and an internal
test lane. It intentionally does not include signing keys, service-account
credentials, or screenshots.

Before publishing an internal test build:

1. Configure release signing outside the repository.
2. Add Play Console service-account credentials through CI secrets.
3. Review the metadata under `metadata/android/en-US`.
4. Build the release artifact with `./gradlew.bat bundleRelease`.
5. Upload a draft internal-test release with
   `bundle exec fastlane android internal`.

<p align="center">
  <img src="app/src/main/res/drawable/veil_official_logo.jpg" alt="VEIL Logo" width="180" />
</p>

# VEIL — Variable Event Identity Layer

<p align="center">

  **[🌐 Visit the official VEIL website](https://puttishiva147.github.io/Veil-Privacy-Focused-/)** · **[Download the Android APK](https://github.com/puttishiva147/Veil-Privacy-Focused-/releases)**

</p>

VEIL is an Android privacy utility designed to help users manage how selected app notifications are handled on their device. The project includes protected-contact rules, notification-listener integration, a private inbox/event interface, biometric support, and a protection service.

> **Current source version:** **4.5.6** (version code **40**), from `app/build.gradle.kts`. Official signed APK releases are published through the GitHub Releases page linked below after the release workflow succeeds.

## VEIL Advertisement Gallery

<p align="center">
  <img src="https://d2ol7oe51mr4n9.cloudfront.net/user_3BvbTfd0Ur59KTcMMTRcfphnn9V/477f1a72-e522-4e5b-9ae0-83b22c723fce.png" alt="VEIL notification protection advertisement" width="80%" />
</p>

<p align="center">
  <img src="https://d2ol7oe51mr4n9.cloudfront.net/user_3BvbTfd0Ur59KTcMMTRcfphnn9V/e4090485-9b6d-4826-8240-13c57feb89ef.png" alt="VEIL privacy by design advertisement" width="80%" />
</p>

## What is in the app?

Features visible in the current source and Android manifest include:

- **Selected-contact protection rules** for contacts the user chooses.
- **Four social messaging sources:** WhatsApp, Instagram, Telegram, and Snapchat are included in package recognition and the dashboard's supported-app UI.
- **Full-screen app switcher:** choose WhatsApp, Instagram, Telegram, or Snapchat on its own page; each app's protection switch is persisted independently, and the dashboard filters conversations, events, and rules to the selected app.
- **Editable Rules tab:** open and edit existing protection rules for any of the four supported apps, with add-rule actions scoped to the selected platform.
- **Responsive empty states:** protected-chat, notification-log, and rules empty states occupy the available content area without pushing messages beneath the bottom navigation.
- **Notification handling** through Android's Notification Listener permission.
- **Private inbox and event-detail screens** for app-managed notification events.
- **Biometric capability** using AndroidX Biometric / Android biometric permission.
- **Protection service** intended to remain active while enabled, subject to Android foreground-service and battery settings.
- **Security and configuration screens**, including a lock/authentication flow.
- **Dark, app-specific Android UI** with launcher identity resources already present in the project.

Actual behavior depends on Android version, user-granted permissions, source configuration, and the originating app's notification behavior. VEIL cannot reliably observe messages typed inside another app unless that app exposes an event through an accessible Android API or notification. It should not be described as a universal message interceptor. Support for an app does not guarantee every notification format, reply action, call action, or media-sharing flow; these capabilities depend on what that app exposes to Android.

## Requirements

- Android 8.0 (API 26) or newer.
- The release APK must come from the official VEIL GitHub Releases page.
- For development: Android Studio, JDK 17, and the Android SDK packages required by the project (compile SDK 36; target SDK 35).

## Download and install VEIL

**[Open official VEIL releases and download the APK](https://github.com/puttishiva147/Veil-Privacy-Focused-/releases)**

If the Releases page does not yet show a release, the maintainer must complete the signing setup below and push the matching version tag. Once the workflow succeeds, the official APK will appear there:

1. Open the official Releases page above.
2. Download the `VEIL-<version>.apk` asset attached to the release. Do not install APKs from unofficial mirrors or untrusted links.
3. Open the downloaded APK on your Android device. If Android prompts you, allow your browser or file manager to **Install unknown apps** for this installation, then return to the APK and continue.
4. Review Android's installer prompts and install VEIL.
5. Open VEIL and grant only the permissions required for the features you intend to use.

If Android reports a signature conflict while updating, the installed copy may have been signed with a different key. Do not uninstall it blindly: uninstalling can erase VEIL's local app data. Obtain an update signed with the same release key, or decide whether the local data can safely be lost before uninstalling.

## Build locally

1. Clone this repository:

   ```bash
   git clone https://github.com/puttishiva147/Veil-Privacy-Focused-.git
   cd Veil-Privacy-Focused-
   ```

2. Open the project in Android Studio and allow Gradle sync to finish. If the Gradle wrapper is present, you can build a debug APK from the repository root:

   **Linux/macOS**
   ```bash
   ./gradlew assembleDebug
   ```

   **Windows**
   ```bat
   gradlew.bat assembleDebug
   ```

3. If the build succeeds, the debug APK is normally written to:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

The repository includes the Gradle wrapper scripts and wrapper JAR. The wrapper downloads the Gradle distribution declared in `gradle/wrapper/gradle-wrapper.properties`. If a build fails, review the reported SDK/plugin requirements and ensure JDK 17 and the Android SDK are installed.

### Optional service configuration

The app applies the Google Services and Secrets Gradle plugins. The current build script configures missing Google Services files to warn rather than fail and refers to `.env` / `.env.example` for secret properties. Check the repository's example configuration and project setup before building. **Never commit API keys, service-account files, signing keys, passwords, or real debug tokens.** Use local environment variables or untracked local configuration for secrets.

## Android permissions and privacy

VEIL's manifest declares permissions including notification posting, notification-listener binding, contacts access, biometric use, vibration, boot completion, foreground services, and a battery-optimization exemption request.

- Grant only the permissions needed for the features you choose to use.
- Notification access can expose notification content from other apps to VEIL. Enable it only if you trust the build and understand the implications.
- Contacts access is used to support selecting protected contacts.
- Android may restrict background services and notification behavior differently across manufacturers and OS versions.
- Review the source code and any configured network/Firebase integrations before using the app with sensitive data.

This README is not a privacy audit or a guarantee that the app is fully offline. Network behavior depends on the source and build configuration.

## Project layout

- `app/src/main/AndroidManifest.xml` — application components and permissions
- `app/src/main/java/com/veil/privacy/` — Kotlin application source
- `app/src/main/res/` — Android layouts, strings, themes, and launcher resources
- `gradle/libs.versions.toml` — dependency and plugin version catalog
- `app/build.gradle.kts` — Android application build configuration

## Development notes

- App namespace / application ID: `com.veil.privacy`
- Minimum Android SDK: API 26 (Android 8.0)
- Target SDK declared in the current app module: API 35
- Compile SDK declared in the current app module: API 36, minor API level 1
- Language: Kotlin
- UI technologies include Jetpack Compose and Android Views / View Binding.
- Tests are configured with JUnit, AndroidX test libraries, and Robolectric dependencies.

## Branding and project identity

The app's source defines the name **VEIL** and expands it as **Variable Event Identity Layer**. The existing launcher resources are part of the project. No claim is made here that the name or logo has been independently verified as an official external brand; the repository's own source is the evidence for the in-app identity.


## Release and version information

- Source version: **4.5.6** (version code **40**).
- The version is authoritative in `app/build.gradle.kts`; release tags must match the version name, for example `v4.5.6`.
- Official release assets are published at [GitHub Releases](https://github.com/puttishiva147/Veil-Privacy-Focused-/releases) only after the release workflow succeeds.
- A source version or successful debug build is not the same as a published, production-signed release.

## Maintainer: configure production signing

Before the first official release, create a dedicated release keystore on a trusted machine. Do not reuse or publish the debug keystore. Keep a secure offline backup of the keystore and both passwords; losing the release key can prevent future updates from installing over the published app.

Example on Linux/macOS (the command prompts for identifying details and passwords):

```bash
keytool -genkeypair -v -keystore "$HOME/veil-release.jks" -alias veil-release -keyalg RSA -keysize 4096 -validity 10000
base64 < "$HOME/veil-release.jks" | tr -d '\n' > "$HOME/veil-release-keystore.base64"
```

Do not put either file inside the repository. In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `RELEASE_KEYSTORE_BASE64`: the contents of the base64 file.
- `RELEASE_STORE_PASSWORD`: the keystore password.
- `RELEASE_KEY_PASSWORD`: the private key password.
- `RELEASE_KEY_ALIAS`: `veil-release` (or the alias you created).

After adding the secrets, securely remove the temporary base64 file and retain the original keystore backup offline. Never paste secret values into issues, commits, or workflow logs.

From the repository root, after the four Actions secrets are configured and after confirming that the tag does not already exist:

```bash
git tag -a v4.5.6 -m "VEIL 4.5.6"
git push origin v4.5.6
```

The tag must match the current `versionName` in `app/build.gradle.kts`. The workflow checks that match before building and publishing.

## Troubleshooting

- **APK won't install:** Confirm the device runs Android 8.0/API 26 or newer, download the APK again from the official Releases page, and check available storage.
- **Android blocks the install:** Review the device's “Install unknown apps” setting for the app used to open the APK. Enable it only for a source you trust.
- **“App not installed” or signature conflict:** Android may reject an APK signed by a different key from the installed copy. Prefer an update signed with the same release key; uninstalling may erase local VEIL data.
- **App behavior differs by device:** Notification access, background services, battery restrictions, and notification rendering vary by Android version and manufacturer. Review VEIL's in-app permissions and Android system settings.
- **Release workflow fails at signing:** The repository owner must configure the four signing secrets described in the release workflow setup instructions. Never commit the keystore or its password.

## Contributing

Small, focused contributions are welcome. Please include:

1. A clear summary of the problem and the proposed change.
2. Reproduction steps for bug fixes.
3. Build or test results, including Android version/device details where relevant.
4. Screenshots for UI changes, without personal notification or contact data.

Avoid committing secrets, generated build output, signing materials, or private user data.

## License

No repository license file was found during this README update. Until a license is added by the project owner, do not assume that the source is licensed for redistribution or reuse. Add a license only after choosing the terms you want to apply.



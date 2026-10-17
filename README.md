# VEIL — Variable Event Identity Layer

VEIL is an Android privacy utility designed to help users manage how selected app notifications are handled on their device. The project includes protected-contact rules, notification-listener integration, a private inbox/event interface, biometric support, and a protection service.

> **Project status:** Android source project. The repository's current Gradle configuration declares version **3.8.0** (version code **18**). This README does not imply that a release APK has been built, tested, or published.

## What is in the app?

Features visible in the current source and Android manifest include:

- **Selected-contact protection rules** for contacts the user chooses.
- **Notification handling** through Android's Notification Listener permission.
- **Private inbox and event-detail screens** for app-managed notification events.
- **Biometric capability** using AndroidX Biometric / Android biometric permission.
- **Protection service** intended to remain active while enabled, subject to Android foreground-service and battery settings.
- **Security and configuration screens**, including a lock/authentication flow.
- **Dark, app-specific Android UI** with launcher identity resources already present in the project.

Actual behavior depends on Android version, user-granted permissions, source configuration, and the originating app's notification behavior. VEIL cannot reliably observe messages typed inside another app unless that app exposes an event through an accessible Android API or notification. It should not be described as a universal message interceptor.

## Requirements

- Android Studio with a compatible Android Gradle Plugin
- JDK 17 for the current Android Gradle Plugin toolchain
- Android SDK platform/build tools required by the project (the app module currently declares compile SDK 36 and minimum SDK 26)
- An Android device or emulator running Android 8.0 (API 26) or newer for installation/testing

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

These are standard project build instructions, not a claim that the current checkout has been successfully built in this environment. If the Gradle wrapper is absent or sync fails, use Android Studio's Gradle tooling and review the reported SDK/plugin requirements.

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

## Contributing

Small, focused contributions are welcome. Please include:

1. A clear summary of the problem and the proposed change.
2. Reproduction steps for bug fixes.
3. Build or test results, including Android version/device details where relevant.
4. Screenshots for UI changes, without personal notification or contact data.

Avoid committing secrets, generated build output, signing materials, or private user data.

## License

No repository license file was found during this README update. Until a license is added by the project owner, do not assume that the source is licensed for redistribution or reuse. Add a license only after choosing the terms you want to apply.

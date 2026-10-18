# VEIL Changelog

## 3.15.1
- Fix APK update compatibility by reusing a persistent debug signing key across GitHub Actions builds instead of generating a new certificate each run.
- Note: the first APK signed with the persistent key cannot update an install signed by a previously discarded ephemeral key; uninstalling that old build may be required once.
- Version code: 26 (previous release: 3.15.0 / 25).


## 3.15.0
- Clip the official in-app JPEG logo to its matching rounded silhouette to hide white corner pixels without altering the source artwork; keep adaptive launcher masking and align its background with VEIL charcoal.
- Add native Android 12+ splash-screen theming and a restrained in-app launch state with asynchronous database initialization, correct authentication routing, and a retry path on initialization failure.
- Add opt-in, configurable volume-button Panic Mode activation (Volume Up/Down, two/three presses) with an explicit foreground-only Android limitation and persisted preferences.
- Improve ON/OFF contrast for the four Protection Rule switches using shared mint/neutral tint selectors; preserve saved rule values.
- Version code: 25 (previous release: 3.14.0 / 24).


## 3.14.0
- Establish a cohesive 2026 visual system with mineral-dark surfaces, jade primary actions, warm amber status, and semantic danger colors.
- Refine Material 3 buttons, cards, chips, dialogs, and sheets; remove default primary-button outlines and soften decorative borders.
- Apply the design tokens to the dashboard and settings surfaces while preserving navigation, functionality, and stored data.
- Version code: 24 (previous release: 3.13.0 / 23).


## 3.13.0
- Auto-confirm unlock when the full configured 4–8 digit master PIN has been entered.
- Persist PIN length metadata without storing plaintext PIN; clear it when credentials are reset.
- Version code: 23 (previous release: 3.12.0 / 22).


## 3.12.0
- Refine Security Setup spacing, copy, and compact VEIL logo presentation.
- Add native system-respecting haptic feedback to setup actions and reduce global card outlines.
- Apply approved VEIL branding to remaining authentication and settings surfaces.
- Version code: 22 (previous release: 3.11.0 / 21).


## 3.11.0
- Apply the uploaded official VEIL logo to launcher and in-app branding.
- Version code: 21 (previous release: 3.10.0 / 20).


## 3.10.0
- Add a clear Android 13+ notification permission explanation dialog with Enable notifications and Not Now actions.
- Only open the system permission prompt after the user chooses Enable; older Android versions and already-granted permission skip this prompt.
- Version code: 20 (previous release: 3.9.0 / 19).


## 3.9.0
- Remove the dashboard header lock icon/button and its unused space; keep Panic Mode and the add-contact action.
- Use neutral contact-protection labels and update related empty-state/help wording.
- Continue supporting persistent, explicitly selected contact/number protection and existing security screens.
- Version code: 19 (previous release: 3.8.0 / 18).


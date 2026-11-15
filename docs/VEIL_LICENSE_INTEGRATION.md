# VEIL Android licensing integration

## Ownership and separation

- Android client repository: this repository.
- Independent website and licensing API repository: https://github.com/puttishiva147/VEIL-Web.
- The Android app contains no payment processing, admin panel, database credentials, license-generation secret or server signing secret.

## User access rules

- The main VEIL UI is not reachable without a valid 12-digit activation code.
- No app sign-up, email login, password login or guest bypass.
- The website issues either a five-day TRIAL code or a PAID subscription code.
- The same activation UI accepts both license categories; the backend determines type and expiry.
- The referral invitation code is separate from the activation code. A qualifying first paid activation adds 15 days to the inviter's existing license.
- Paid renewals retain the existing activation code and extend the existing license server-side.

## Activation protocol

1. Android generates a 2048-bit RSA key pair in Android Keystore.
2. Android sends its PEM public key and SHA-256 hash of the app-scoped Android ID to `POST /api/v1/activation/challenge`.
3. The backend returns a one-time nonce bound to the public-key fingerprint.
4. Android signs the nonce with the non-exportable private key using SHA256withRSA.
5. Android submits the 12-digit code, public key, device fingerprint hash, nonce and signature to `POST /api/v1/activation/activate`.
6. The backend validates the code digest, expiry, status, one-device binding, challenge expiry and signature.
7. The backend returns a short-lived bearer token. Android stores the token and code encrypted using Android Keystore AES-GCM.
8. On app entry, Android calls `POST /api/v1/licenses/status`; the backend rechecks the license and device and returns a fresh short-lived token.

The current token lifetime is 15 minutes. MainActivity performs online verification on app entry. When the persistent protection service is enabled, its watchdog revalidates every two minutes; failure stops the shield and the notification listener fails closed when the token expires. If the service is disabled, an already-issued token can remain locally valid until its 15-minute expiry, after which the activity gate requires online verification. No local premium flag is authoritative.

## Android build configuration

Set `VEIL_LICENSE_API_BASE_URL=https://<deployed-website-host>/api/v1` in the Android build environment or local `.env`. The placeholder in `.env.example` is intentionally not a production endpoint. The Google Secrets Gradle plugin exposes the property through BuildConfig. Never add the website's database URL, HMAC peppers, payment keys, admin secrets or private signing keys here.

## Notification and service gating

- `MainActivity` validates the license before routing to existing security setup/dashboard.
- The Application lifecycle guard routes non-gate screens to the activation screen when the local token expires.
- The notification listener and notification reply receiver refuse to process or send without a locally valid license token.
- The persistent protection service checks authorization at startup and periodically refreshes it.
- Existing local PIN/privacy protection remains separate from licensing; it is not a VEIL account login.

## Referral client

The Settings screen opens the referral page. The app fetches `GET /api/v1/referrals/me` with the current bearer token and allows copying or sharing the referral code. The invite message links to the website's five-day trial and instructs the invitee to enter the inviter's referral code at paid checkout.

## Limitations / deployment requirements

- Production activation cannot succeed until the website API is deployed and its HTTPS origin is configured here.
- The backend needs its own production PostgreSQL database, server-only secrets, administrator TOTP/password provisioning and Razorpay credentials/webhook configuration.
- A device reset after uninstall/replacement requires the website admin's audited device-reset action.
- The client fails closed when the API is unreachable; a user-visible offline grace policy has not been implemented.

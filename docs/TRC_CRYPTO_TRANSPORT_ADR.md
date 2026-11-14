# TRC Architecture Decision Record: Cryptography and Nearby Transport

Status: evaluation record; no production dependency is approved.

Date: 2026-11-14

## Decision summary

Do not add a cryptographic library or radio SDK merely to make the prototype appear functional. Keep the provider and transport interfaces isolated until a two-device proof-of-concept, licence review, Android compatibility check, and threat review are complete.

Current preferred evaluation order:
1. Cryptography: compare Signal libsignal with Matrix vodozemac / matrix-rust-sdk integration. Do not use legacy libolm.
2. Nearby transport: prototype native Android Wi-Fi Direct and compare it with Google Nearby Connections on real supported devices.
3. Keep the message envelope and authenticated session layer transport-independent.

## Cryptography candidates

### A. Signal libsignal

Strengths:
- Implements Signal Protocol components including Double Ratchet.
- Provides Android native artifacts and Java-facing APIs.
- Familiar protocol family and extensive use in Signal's own clients.

Blocking concerns:
- The upstream repository says use outside Signal is unsupported and APIs/implementations can change without notice.
- The repository is AGPL-3.0. VEIL's distribution and source-release obligations need a qualified licence review before adoption.
- Android build instructions require Rust, Android NDK targets and officially supported JDK 21; the current VEIL CI uses JDK 17. This is a real build-pipeline compatibility change, not a one-line dependency addition.
- Published Android artifacts and transitive native libraries need ABI, packaging, reproducibility, update and vulnerability-management review.
- Upstream's statement that external use is unsupported means the team should not assume stable third-party support.

Conclusion: technically relevant, but not approved until licence, build and support risks are resolved.

### B. Matrix vodozemac / matrix-rust-sdk

Strengths:
- vodozemac is the Rust successor to libolm; Matrix documents an independent audit and recommends migration away from deprecated libolm.
- vodozemac and matrix-rust-sdk are Apache-2.0.
- matrix-rust-sdk is used by Element X and has Kotlin bindings through UniFFI.

Blocking concerns:
- vodozemac implements Matrix Olm/Megolm semantics; it is not automatically a drop-in replacement for Signal's X3DH/PQXDH plus Double Ratchet design.
- matrix-rust-sdk is a Matrix client-server SDK. Adopting it means evaluating Matrix accounts, homeservers, protocol metadata, sync and storage semantics—not just embedding a generic crypto primitive.
- Its Kotlin FFI APIs follow Element X's needs and are not stable across releases.
- The SDK's normal client model includes persistent event/timeline capabilities that conflict with TRC's strict no-persistent-plaintext default unless carefully isolated and configured.

Conclusion: a credible Apache-licensed ecosystem to investigate, but not yet proven suitable for TRC's custom peer-to-peer and relay model. Do not add the full Matrix SDK just to obtain encryption.

### C. Legacy libolm

Conclusion: reject. Matrix officially deprecated it in 2024 and recommends vodozemac due to memory-safety and cryptographic implementation concerns. Do not add `org.matrix.android:olm-sdk` as a shortcut.

## Nearby transport candidates

### A. Google Nearby Connections

Strengths:
- Supports discovery, symmetric connection acceptance and encrypted peer-to-peer data transfer.
- Works without internet for nearby devices.
- Abstracts Bluetooth, BLE and Wi-Fi and supports byte, file and stream payloads.

Trade-offs:
- Google Play services Nearby collects usage analytics including discovery/connection performance and device information; users can control diagnostics collection in device settings.
- Requires Google Play services compatibility and makes that service part of the trust/availability model.
- Its transport encryption does not replace TRC application-level E2EE or identity verification.

Conclusion: strong rapid-prototype option, but not a zero-telemetry option. Keep as a candidate, not an automatic default.

### B. Native Android Wi-Fi Direct

Strengths:
- Direct platform API; no Google Nearby SDK analytics dependency.
- Can establish a local peer-to-peer Wi-Fi connection without internet and use sockets for larger transfers.
- Gives TRC more direct control over discovery, connection and data flow.

Trade-offs:
- Android 13+ requires `NEARBY_WIFI_DEVICES`; the official guide also documents location and Wi-Fi state permissions. Some discovery APIs require Location Mode to be enabled.
- OEM behavior, permissions, radio state, lifecycle, group-owner negotiation and connection recovery need real-device testing.
- The app must implement secure peer discovery, authenticated handshakes, socket framing, flow control, reconnects and resource limits.
- Wi-Fi Direct link security is not a substitute for application-level E2EE.

Conclusion: preferred first native transport experiment if privacy control is weighted above implementation speed. This is a prototype choice only; device compatibility must be measured.

### C. Bluetooth LE

Conclusion: keep for discovery or small control messages only. Do not design media transfer around BLE throughput. A separate authenticated high-bandwidth path is needed for media.

## Recommended staged experiment

1. Keep all payloads as versioned ciphertext envelopes.
2. Build a Wi-Fi Direct spike that exchanges only a fixed-size random test payload between two devices; no chat UI, location or plaintext.
3. Measure discovery time, connection success, reconnect behavior, permission friction, battery impact, OEM reliability and failure recovery.
4. Build the same spike with Nearby Connections; record the Play services dependency and diagnostics trade-off in the user-facing privacy notice.
5. Compare results on multiple Android versions and OEMs before selecting the default.
6. Only after a transport is selected, implement authenticated session establishment and message transfer. Do not equate a radio connection with a trusted peer.
7. Separately build a cryptographic adapter spike with the chosen audited protocol, test vectors and two-device identity verification. No production messages until protocol and licence reviews pass.

## Explicit unresolved decisions

- Whether VEIL's source and distribution model can comply with AGPL-3.0 if libsignal is selected.
- Whether Matrix's Olm/Megolm semantics fit TRC's independent peer identity and no-server-first design.
- Whether the project is willing to depend on Google Play services diagnostics for Nearby Connections.
- Whether a strict no-persistent-relay policy is more important than asynchronous message delivery.
- Whether the app will support only devices with Google Play services or also AOSP/no-GMS devices.

## Source references

- Signal libsignal repository and README: https://github.com/signalapp/libsignal
- Matrix libolm deprecation and vodozemac recommendation: https://matrix.org/blog/2024/08/libolm-deprecation/
- Matrix Rust SDK: https://github.com/matrix-org/matrix-rust-sdk
- Google Nearby Connections overview and data collection: https://developers.google.com/nearby/connections/overview
- Android Wi-Fi Direct guide: https://developer.android.com/develop/connectivity/wifi/wifi-direct

These references describe upstream project and platform behavior; they are not an independent security audit of TRC or a legal opinion.

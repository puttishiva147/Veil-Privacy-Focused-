# TRC Security Threat Model and Engineering Decisions

Status: design baseline for the TRC beta. This is not a security certification.

## 1. Security objectives

TRC is an optional communication feature inside VEIL. The first release target is a reliable, auditable one-to-one private chat—not an unbounded promise of invisibility.

Primary objectives:
- End-to-end confidentiality and integrity for message contents and media.
- Peer authentication: users can detect an unexpected identity/key change.
- Forward secrecy and post-compromise recovery, provided by the selected protocol.
- Replay resistance, bounded message lifetime, and safe session reset.
- Minimise metadata and avoid collecting location unless the user explicitly enables it.
- Keep TRC changes isolated from VEIL notification interception and protection.
- Make failure states visible: offline, pending, expired, unverified, blocked, and delivered must not be conflated.

Non-goals for beta:
- Guaranteed anonymity from mobile carriers, ISPs, operating systems, or a global network observer.
- Guaranteed forensic erasure on rooted/compromised devices or flash storage.
- Guaranteed offline delivery over arbitrary distances without a path or store-and-forward relay.
- Multi-hop mesh, group chat, voice/video calls, or decentralised identity infrastructure.
- Hiding the existence, timing, or size of all network traffic.

## 2. Assets and trust boundaries

Assets:
1. Plaintext message and media while being composed or rendered.
2. Long-term identity private key.
3. Session/ratchet state and ephemeral private keys.
4. Peer identity/fingerprint and trust decision.
5. Ciphertext queued for delivery and delivery receipts.
6. Optional location fix and derived distance.
7. Pairing QR offer and its one-time private material.
8. User-visible security state and message expiry metadata.

Trust boundaries:
- UI <-> application domain layer.
- Application <-> cryptographic provider.
- Application <-> local radio/transport APIs.
- Device <-> peer device.
- Device <-> relay/server and hosting provider.
- Android OS, keyboard, clipboard, notifications, backups, accessibility services.
- Build pipeline, dependency repositories, release signing, and update channel.

The operating system and endpoint are not trusted to be uncompromised. A secure protocol cannot protect plaintext from a compromised endpoint at the moment it is displayed.

## 3. Adversaries

Consider:
- Passive network observer recording IPs, timing, packet sizes, and connection patterns.
- Malicious or impersonating peer presenting a forged/replaced QR.
- Active man-in-the-middle during pairing or reconnect.
- Relay operator attempting to read, alter, retain, correlate, or selectively drop traffic.
- Nearby attacker attempting discovery flooding, replay, spoofing, or denial of service.
- Malicious app with access to notifications, accessibility, clipboard, screen capture, or backups.
- Lost/stolen device attacker with physical access, weak lock screen, or unlocked app.
- Compromised build dependency, CI workflow, signing key, or update artifact.
- Abusive authenticated peer attempting spam, huge payloads, malformed media, or location inference.

## 4. Security requirements and controls

### Identity and pairing
- QR offers are short-lived, single-use, versioned, size-bounded, and signed by an identity key.
- QR scan is only an invitation; it does not silently establish trust.
- Both users must see a stable identity fingerprint or short authentication string and explicitly confirm it.
- A QR must not carry a long-term private key, location, phone number, IP address, or message content.
- Reject expired offers, invalid signatures, unsupported versions, duplicate/replayed offers, malformed fields, and unreasonable clock windows.
- Process-local replay protection is only a best-effort guard: it resets on process death. The eventual authenticated session protocol must independently prevent replay.
- Key changes require an explicit warning and a fresh verification step; never silently overwrite a trusted identity.

### End-to-end cryptography
- Do not write a home-grown ratchet, key agreement, signature scheme, or AEAD wrapper.
- Select a maintained protocol and implementation only after API stability, Android ABI support, licence compatibility, maintenance posture, security disclosures, and integration cost are reviewed.
- Required properties: authenticated key agreement, forward secrecy, post-compromise recovery, authenticated encryption, replay handling, identity-key continuity, and secure session reset.
- Persist only encrypted protocol state using Android Keystore-backed key protection where supported. Keystore protects local key material; it does not itself implement E2EE.
- Define a versioned ciphertext envelope and authenticated associated data (protocol version, sender, recipient, message ID, payload type, expiry).
- Never treat transport encryption (for example, Nearby Connections encryption) as a replacement for application-level E2EE.

### Transport and relay
- Separate the message protocol from transport. The same ciphertext envelope must work over nearby and internet paths.
- A transport is selected only after actual discovery/connection succeeds. A theoretical range or a boolean internet-available flag is not proof that the peer is reachable.
- Authenticate the peer at the application layer after every new transport connection.
- Nearby Connections can provide offline peer-to-peer transfer and encrypted transport, but its Google Play services implementation documents collection of performance/device diagnostics. That creates a product/privacy trade-off; disclose it and evaluate a direct platform alternative before claiming zero telemetry.
- BLE is appropriate for discovery/control and small payloads, not assumed high-throughput media delivery.
- Wi-Fi Direct / Nearby high-bandwidth paths are preferred for media where supported.
- Same-LAN transfer requires authenticated peer discovery and protection against local-network spoofing.
- Internet relay stores ciphertext only. A relay can still observe IP addresses, timing, sizes, account/route identifiers, and delivery patterns.
- If strict no-persistent-relay-storage is selected, relay availability becomes best-effort and devices must be online together. If asynchronous delivery is required, a bounded ciphertext mailbox is required and must have an explicit retention policy.
- No hidden fallback to an unapproved transport. Show the selected path and whether delivery is queued.

### Location
- Default OFF. Request permission only after a user action that needs it.
- Distance-only mode is not location secrecy: if coordinates are exchanged, the peer can infer the shared coarse location.
- Do not call ordinary coordinate rounding private distance computation. A future privacy-preserving distance protocol requires a separate cryptographic review and abuse analysis.
- Mutual live location requires both users to approve the same time-bounded session, with a visible stop control.
- Never send location to relay, analytics, crash logs, or pairing QR.
- Do not retain location history. Stop collection immediately on revoke, disconnect, or expiry.
- Explain that cellular networks and the OS can still infer device location.

### Messages, media, expiry
- 24 hours is a message TTL policy, not a promise of secure erasure.
- Expiry is enforced on both endpoints and relay queues; deletion failures must be observable in tests and documented.
- The relay must reject expired envelopes and cap queue size, message size, per-peer rate, and total mailbox storage.
- No plaintext in Room, Firebase, logs, crash reports, analytics, clipboard, notification previews, or media thumbnails.
- Media must be encrypted before leaving the device; filenames, EXIF, thumbnails, and previews need explicit sanitisation policy.
- Render media through bounded decoders; reject malformed/oversized inputs and decompression bombs.
- Disable OS backup for TRC secrets/state or provide an explicit, reviewed encrypted backup/export design.
- FLAG_SECURE is a deterrent, not a guarantee against all screenshots, cameras, rooted capture, or OS-level compromise. Apply it on all TRC windows.

### Abuse and resilience
- Bound QR size, payload size, media dimensions, pending queue, connection attempts, retries, and concurrent sessions.
- Rate-limit discovery/connection and message submission.
- Treat all peer payloads as hostile input. Validate schema before decrypt/render; validate decrypted content before UI/media parsing.
- Add block/report controls that do not silently expose plaintext to a server.
- Prevent unauthenticated peers from triggering expensive crypto, media decode, or location access.
- Use monotonic time for in-process deadlines where possible; handle wall-clock rollback for persisted expiry safely.
- Make delivery state explicit and idempotent to avoid duplicate messages after transport switches.

## 5. Data lifecycle

| Data | In memory | Persistent local | Relay |
|---|---|---|---|
| Plaintext message | During compose/render only | Never by default | Never |
| Ciphertext envelope | While sending/receiving | Only if an approved encrypted outbox is required | Bounded TTL queue only if async delivery is enabled |
| Identity private key | Loaded only as required | Keystore-protected key material | Never |
| Session/ratchet state | Active session | Encrypted, protocol-specific state if required | Never |
| Location fix | Only during explicit session | Never | Never |
| QR offer | Until expiry/use | Never | Never |
| Media plaintext | During capture/render | No plaintext cache | Never |
| Delivery metadata | Short-lived | Minimal state only | Minimum required to route/expire |

Any deviation from this table requires an explicit architecture decision and a user-facing explanation.

## 6. Transport strategy

Phase 1:
- Implement one nearby transport adapter and one internet relay adapter behind the same interface.
- Prefer Google Nearby Connections for a fast offline prototype only after documenting its Google Play services diagnostics implications and checking distribution/device compatibility.
- Keep Wi-Fi Direct as an alternative if the product rejects the diagnostics trade-off or needs a direct platform-controlled path.
- Do not implement BLE GATT, Wi-Fi Direct, Nearby Connections, LAN sockets, and mesh simultaneously. Each adds permissions, lifecycle, device-compatibility, and security surface.

Phase 2:
- Add failover with authenticated session continuity and duplicate suppression.
- Add encrypted media transfer and resumable chunks.
- Evaluate store-and-forward only after relay metadata and retention policy are approved.

Phase 3 (research only):
- Multi-hop mesh, traffic obfuscation, privacy-preserving distance, and group messaging. Each requires separate threat models and test plans.

## 7. Cryptographic dependency decision gate

Signal's libsignal is a technically relevant candidate, but its own repository states that use outside Signal is unsupported, APIs may change without notice, and its repository is AGPL-3.0. Do not add it as a casual dependency. Before adoption:
1. Obtain a licence review for the VEIL distribution model.
2. Pin an exact compatible release and inspect its Android AAR/JNI ABI footprint.
3. Verify supported Android API levels, ABI splits, build reproducibility, and upgrade path.
4. Review its public security policy, known issues, maintenance cadence, and API compatibility.
5. Build a thin adapter with test vectors; do not expose library-specific types through the TRC domain model.
6. If the licence/support terms are unsuitable, evaluate other maintained, independently reviewed protocol implementations; do not substitute a custom cryptographic design.

No production E2EE dependency is approved by this document.

## 8. Android and project isolation

- TRC remains under package com.veil.privacy.trc.
- Do not change VEIL's notification listener, protection service, or unrelated database/navigation as part of the foundation.
- Request nearby/location permissions just-in-time and only for the feature invoked.
- Background reliability is not guaranteed if Android kills the process; foreground-service use must comply with current Android policy and be justified.
- Keep TRC activities non-exported unless an explicit, validated deep-link/QR entry point is required.
- FLAG_SECURE must be applied before sensitive content is rendered.
- The custom in-app keyboard is a custom view, not an Android IME. It must not request system IME privileges. Clipboard use should be opt-in and clearly warned.
- Release signing keys must never be committed; CI signing keys are test-only and cannot be used for public production releases.

## 9. Beta acceptance gates

A beta is not ready until all are true:
1. CI passes unit tests, lint, debug build, and release build.
2. Two-device pairing passes success, expiry, replay, tamper, identity mismatch, cancellation, and clock-skew tests.
3. Identity verification is explicit and key-change warnings are tested.
4. Protocol integration passes published test vectors and independent security review.
5. Text delivery passes offline, reconnect, duplicate, reordered, expired, and transport-switch tests.
6. Relay tests prove no plaintext reaches server logs/storage and TTL/rate/size limits work.
7. Android lifecycle tests cover process death, permission revoke, background restrictions, reboot, and low-memory.
8. TRC regression tests show existing VEIL notification interception remains unchanged.
9. Privacy review covers logs, analytics, crash reporting, backups, clipboard, screenshots, media metadata, and location.
10. UI labels pending/unverified/expired correctly and never implies delivery or security that did not occur.

## 10. Open decisions (must not be silently assumed)

- Is asynchronous internet delivery required, or is online-at-the-same-time delivery acceptable?
- Is Google Play services Nearby acceptable given its documented diagnostics collection?
- What licence can VEIL legally distribute?
- What is the account/identity recovery model if a device is lost?
- Is a local encrypted outbox acceptable, or must all messages be memory-only even at the cost of delivery reliability?
- Are group chats, calls, multi-hop mesh, and cross-platform clients explicitly out of beta scope? Recommended: yes.

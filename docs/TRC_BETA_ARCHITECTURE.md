# VEIL TRC — Beta Architecture

Status: foundation scaffold only; this is not a working chat implementation.

## Product boundary
TRC (The Real Chat) is an experimental feature inside the existing VEIL Android application, not a separate app. It must not regress VEIL notification protection.

## Agreed direction
- One-to-one chat first: text, emoji, reactions, GIF references, encrypted media.
- Pair devices by scanning a short-lived QR pairing code.
- Hybrid communication: nearby offline links plus long-distance internet relay.
- Evaluate Bluetooth LE, Wi-Fi Direct, same-LAN transfer, and Google Nearby Connections.
- Multi-hop mesh is a later experiment, not part of the first deliverable.
- Default message lifetime: 24 hours from sending.
- No persistent plaintext chat history.
- TRC-owned in-app keyboard; the composer must not invoke Android's software IME.
- FLAG_SECURE on every TRC screen.
- Location sharing defaults to OFF; distance-only and mutual live-location require explicit mutual consent.

## Location privacy
1. GPS fixes stay on-device unless both peers explicitly approve a location-sharing session.
2. QR pairing contains temporary pairing material, never GPS coordinates.
3. In distance-only mode, a coarse fix may be exchanged only inside the authenticated E2EE peer session; each endpoint calculates distance locally.
4. This is NOT private-set-intersection or secure two-party distance computation: the peer can infer the approximate shared location. Do not call it invisible to the peer.
5. Never send coordinates to relays, Firebase, analytics, crash reports, or logs.
6. Stop collection when sharing is disabled or the session ends. Keep no location history.
7. Cellular operators can still infer approximate device location from network infrastructure. The app cannot promise carrier-untraceability.

## Message and storage model
- The application layer accepts ciphertext envelopes only.
- Plaintext exists only while composing/rendering and is cleared when the view closes or the message expires.
- No Room/SQLite, Firebase, file cache, clipboard history, analytics, or crash-report persistence for TRC message content.
- A relay may hold ciphertext in volatile RAM only, with strict TTL. RAM queues are temporary storage, not zero-storage.
- If no route exists and no relay is available, delivery remains pending or fails. Offline delivery cannot be guaranteed without a reachable path or temporary store.
- Android/OS backups, rooted devices, external cameras, and compromised endpoints remain outside complete app control.

## Cryptographic protocol
Do not invent cryptography. Integrate a maintained, independently audited E2EE protocol after compatibility and licensing review. Evaluate PQXDH / Double Ratchet or an equivalent maintained protocol. Require identity verification, replay protection, authenticated acknowledgements, key rotation, and session reset handling.
Do not implement custom relay routing until threat modelling covers traffic analysis, metadata leakage, replay, abuse, and denial of service. Onion routing or mixnet relays reduce some exposure but cannot guarantee resistance to a global observer.

## Network selection
Select only among routes already confirmed reachable by platform adapters:
1. Wi-Fi Direct
2. Bluetooth LE
3. Same-LAN transfer
4. Available mesh route
5. Internet relay

Distance is not proof of radio reachability. Re-check route health and authenticate the peer independently of transport.

## Android integration
- Keep TRC under package com.veil.privacy.trc.
- Do not modify the existing notification listener, protection service, database, or navigation until the feature entry point and regression tests are ready.
- Apply FLAG_SECURE to every TRC screen.
- Request location and nearby-device permissions only when the user enables the corresponding feature.
- The custom in-app keyboard is not an Android IME and must not request the system keyboard.
- Use a maintained QR scanner and short-lived pairing payloads; never log QR contents.

## Scaffold contents
The initial source scaffold contains:
- Domain enums and a ciphertext-only envelope model.
- Coarse-location and local distance-estimation helpers.
- A transport selection policy.
- A 24-hour expiry policy.
- A FLAG_SECURE Activity helper.

These files are not yet connected to UI, Android radio APIs, QR scanning, a cryptographic protocol, or a relay backend. Do not market this scaffold as functional E2EE or anonymous messaging until those parts are implemented and independently tested.

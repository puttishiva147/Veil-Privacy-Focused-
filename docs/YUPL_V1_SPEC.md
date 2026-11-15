# YUPL v1 — VEIL Universal Language Protocol

**Status:** Notification transport foundation is implemented. The codec contract and a development fixture exist, but production dictionary installation is intentionally pending the owner's authoritative language file. This is not an encryption specification.

## Scope

YUPL v1 is a deterministic, lossless text encoding used by VEIL-to-VEIL messages transported inside supported messaging-app notification reply actions. The messaging application remains the transport; VEIL does not call a separate messaging API or operate a messaging server.

## Codec format

The current development fixture uses dictionary version **1** and payload prefix `Y1;`. It is not the production dictionary. The production version and mappings must be derived from the owner's supplied file; do not enable the fixture as a fallback.

- Exact lowercase phrases in the built-in phrase table use a compact phrase token `pNN`.
- Known words use a dedicated Unicode symbol plus a case marker: `l` lowercase, `u` uppercase, `t` title case.
- Unknown words, mixed-case words, numbers, whitespace, punctuation and unsupported Unicode are preserved with a lossless UTF-8 Base64URL fallback token `x...`.
- Tokens are separated by a period. Base64URL does not contain a period, so token boundaries are unambiguous.
- Unknown or malformed token forms are rejected. The decoder never silently substitutes text.

The development fixture's fallback makes arbitrary Unicode round-trip correctly, including Hindi, Telugu, Tamil, Arabic and emoji. The authoritative dictionary must be tested for the same lossless property before activation. The payload is reversible and must not be treated as cryptographic secrecy.

## Outer transport frame

The message text sent through RemoteInput is:

`YUPL1:<base64url-frame>`

The binary frame contains:
1. Magic value `VEIL-TRANSPORT`.
2. Frame format version.
3. Unique message ID.
4. YUPL dictionary version.
5. Encoded YUPL payload.
6. SHA-256 digest of the preceding frame bytes.

The receiver validates the frame, digest, message ID and dictionary version before decoding. Current packet size limit is 2 KiB. A duplicate message ID is ignored by the durable transport queue.

## Android transport

- Incoming messages are inspected by Android `NotificationListenerService`.
- Outgoing messages use an existing live notification `RemoteInput` reply action from the selected supported app.
- No separate messaging API or VEIL messaging server is used.
- If there is no live reply action, the message remains queued locally; VEIL does not pretend it was sent.
- `DISPATCHED_NOT_CONFIRMED` means Android accepted the reply PendingIntent, not that the recipient received or read it.
- If the notification disappears, the action expires, the app truncates the packet, or Android/OEM policy blocks the action, transport can fail. The user must see the real queue/error status.
- Current reply-action adapters are listed in `VeilTransportManager.supportedTransports()`: WhatsApp, WhatsApp Business, Instagram, Snapchat and Telegram. A listed app is usable only when Android exposes a live compatible RemoteInput action for that notification; this is not a guarantee that every version/device supports it.

## Security limitations — important

YUPL v1 is **encoding/obfuscation, not encryption**:
- The dictionary is part of the Android application and can be reverse-engineered.
- Base64URL fallback is trivially reversible.
- SHA-256 in the outer frame detects accidental corruption but is unkeyed; it does not authenticate a sender or stop a malicious party from constructing a new packet.
- The outer messaging app's own encryption is separate from YUPL and does not automatically create a VEIL end-to-end identity or key agreement.

Do not describe YUPL v1 as end-to-end encrypted. A future encrypted YUPL version needs a reviewed key-establishment/pairing protocol, per-conversation keys, authenticated encryption (for example AES-GCM or ChaCha20-Poly1305), replay protection, rotation and recovery. That key-establishment decision is intentionally not invented in this implementation.

## Compatibility

- Dictionary versions are immutable once released.
- A decoder must reject an unsupported version and retain the encoded packet for later processing.
- Future dictionary updates must include version metadata, integrity validation and backward-compatibility tests.
- Frame format version and dictionary version are separate version numbers.

## Verification

Unit tests cover the development fixture's deterministic encoding, exact round-trip, phrase and word tokens, case preservation, punctuation, whitespace, unknown words, multilingual Unicode, emoji and malformed payload rejection. These tests do not certify the owner's future dictionary file. After it is supplied, add file parser/validation tests and repeat the full round-trip suite against the authoritative data. Android integration still requires real-device testing against each target app's notification/RemoteInput behavior.

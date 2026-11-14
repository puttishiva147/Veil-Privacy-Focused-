# TRC QR Pairing — Implementation Notes

## Offer lifecycle
- QR offers expire after two minutes.
- QR payload includes a protocol version, random session ID, 256-bit random nonce, peer ID, identity public key, one-time ephemeral public key, expiry, and identity signature.
- QR payload must never contain GPS coordinates, phone numbers, IP addresses, message text, or long-lived private keys.
- Scanning alone does not establish trust. The receiving device validates the signature and displays an identity confirmation step.
- A secure channel is considered established only after the audited E2EE adapter completes authenticated key agreement.
- Pairing offers and session state are memory-only and discarded on expiry, rejection, app process death, or completion.

## Canonical signature transcript
The identity signature must authenticate the canonical byte sequence produced by `TrcPairingTranscript.encode`. This is a serialization contract, not a cryptographic primitive.

Encoding, in order:
1. ASCII domain separator `VEIL-TRC-PAIRING-V1`, followed by one zero byte.
2. Protocol version as unsigned 32-bit big-endian integer (currently 1).
3. Peer ID as UTF-8, prefixed by unsigned 16-bit big-endian byte length.
4. Session ID as ASCII, prefixed by unsigned 16-bit big-endian byte length.
5. Nonce as exactly 32 bytes, prefixed by unsigned 16-bit big-endian byte length.
6. Identity public key bytes, prefixed by unsigned 16-bit big-endian byte length.
7. One-time ephemeral public key bytes, prefixed by unsigned 16-bit big-endian byte length.
8. Expiry timestamp as positive 64-bit big-endian integer milliseconds.

The signature itself is not included in the signed transcript. Any change to peer ID, session ID, nonce, identity key, ephemeral key, protocol version, or expiry must invalidate the signature. Implementations must use this exact serialization; do not sign ad-hoc JSON or platform-dependent string formatting.

## Security boundaries
- QR validation is not peer authentication until the signature is checked against the included identity key and the user verifies the peer identity through a separate trusted comparison step (for example, comparing a short authentication string in person).
- A valid signature proves control of the corresponding private key; it does not prove that key belongs to the person the user intended to contact.
- The current replay guard is process-local and bounded. The authenticated session protocol must independently prevent replay and duplicate session establishment.
- The pairing crypto interface is only a boundary. No production cryptographic implementation is included.
- The QR scanner UI, QR renderer, identity verification UI, and actual E2EE key agreement remain to be integrated.

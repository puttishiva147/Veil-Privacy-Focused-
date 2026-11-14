# TRC QR Pairing — Implementation Notes

- QR offers expire after two minutes.
- QR payload includes a protocol version, random session ID, 256-bit random nonce, peer ID, identity public key, one-time ephemeral public key, expiry, and identity signature.
- QR payload must never contain GPS coordinates, phone numbers, IP addresses, message text, or long-lived private keys.
- Scanning alone does not establish trust. The receiving device validates the signature and displays an identity confirmation step.
- A secure channel is considered established only after the audited E2EE adapter completes authenticated key agreement.
- Pairing offers and session state are memory-only and discarded on expiry, rejection, app process death, or completion.
- This code defines the wire-format boundary and state machine only. No production cryptographic implementation is included.
- The QR scanner UI, QR renderer, identity verification UI, and actual E2EE key agreement remain to be integrated.

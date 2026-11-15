# VEIL Multi-App Coded Transport

## Scope

This module prepares VEIL to carry its future private-language packets through the Android notification mechanisms already used by VEIL. It does not use WhatsApp, Instagram, or Snapchat messaging APIs.

Supported transport package IDs:

- WhatsApp: `com.whatsapp`
- Instagram: `com.instagram.android`
- Snapchat: `com.snapchat.android`

## Message path

### Outgoing

1. A user writes a message in VEIL Protected Chat.
2. `VeilTransportManager` asks the VEIL language codec to encode it.
3. The encoded tokens are wrapped in a versioned `VUPL1:` transport packet.
4. VEIL selects an app for which Android has exposed a live notification `RemoteInput` reply action for that contact.
5. VEIL submits the packet through that app's existing `PendingIntent`.
6. Android accepting the `PendingIntent` is recorded as `DISPATCHED_NOT_CONFIRMED`; VEIL cannot infer delivery or read status.

If the dictionary is not installed, the message is stored locally as `WAITING_FOR_DICTIONARY` and is not sent as plaintext. If no reply action is active after encoding, it is stored as `WAITING_FOR_REPLY_ACTION`.

### Incoming

1. `VeilNotificationListener` receives a supported-app notification.
2. The notification parser extracts the visible sender and message text.
3. VEIL looks for a valid `VUPL1:` packet.
4. The packet is validated and stored in the Room transport queue.
5. The installed dictionary decodes the token payload.
6. The decoded text is added to the existing Protected Chats history.

Transport reception and reply-action registration run independently of the notification-disguise master toggle.

## Dictionary integration

`VeilLanguageCodec` is the adapter contract for the official dictionary file. The current `DictionaryPendingCodec` intentionally refuses to encode or decode. Replace it with the dictionary-backed implementation when the user supplies the language file, then call `processPendingQueues()` to process queued messages.

## Packet format

The current envelope uses a version marker and URL-safe Base64 framing around a binary record containing:

- protocol magic and format version
- message identifier
- dictionary version
- encoded language payload
- SHA-256 digest for corruption detection

The digest is unkeyed and is not encryption or authentication against an active attacker. Confidentiality requires a separate authenticated-encryption layer and secure key exchange.

## Platform constraints

- Android Notification Listener access can inspect notification content exposed by the source app.
- Outgoing sending is possible only if the source app currently exposes a usable inline-reply `RemoteInput` action.
- Not every Instagram or Snapchat notification exposes such an action; availability is runtime-dependent.
- VEIL cannot silently send arbitrary messages through apps that do not expose a reply action.
- When no action is available, the packet stays queued until a suitable notification action appears.
- Notification formats, OEM behavior, app versions, and Android restrictions can change behavior.
- The app's acceptance of a reply PendingIntent is not proof of delivery.

## Current implementation status

Implemented in the transport branch:

- Room transport queue and database migration.
- App-scoped reply-token registry.
- Multi-app transport selection based on live reply actions.
- Packet framing and corruption checks.
- Incoming packet capture.
- Protected Chat queue integration.
- Deferred dictionary queue and retry hooks.
- Packet unit tests.

Not yet implemented:

- Official dictionary file and real codec.
- Authenticated encryption and key management.
- Chunking/reassembly for oversized messages.
- Reliable contact identity binding across different apps.
- Device-to-device integration testing with real WhatsApp, Instagram, and Snapchat versions.
- End-to-end UI for selecting and managing the preferred transport app.

## Testing

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

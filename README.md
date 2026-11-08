# Stade

Stade is a peer-to-peer encrypted chat app with no central server. Every message travels directly between devices — over Tor onion services or the local network — and is protected by a post-quantum-hybrid encryption scheme. There are no accounts, no phone numbers, and no servers that ever see your messages or metadata.

Built with Kotlin Multiplatform and Compose Multiplatform, Stade runs natively on **Android**, **Windows**, **macOS**, and **Linux** from a single shared codebase.

## Core principles

- **No servers.** Messages go directly from sender to recipient. There is nothing in the middle to breach, subpoena, or go down.
- **No accounts.** Your identity is a self-sovereign `STADE-XXXXXXXXXXXX` ID, derived from your own keys — nothing is registered anywhere.
- **Post-quantum by default.** Every conversation is protected against both today's attackers and tomorrow's quantum computers.

## Security & Encryption

- **Hybrid post-quantum handshake** — session keys are established from a combination of classical X25519 key agreements and **ML-KEM-768** (a post-quantum key encapsulation mechanism). Both sides contribute single-use ephemeral keys that are discarded immediately afterwards, so a conversation cannot be recovered later even if the long-term identity keys are seized.
- **Hybrid post-quantum Double Ratchet** — every message is encrypted with its own key, and every ratchet step mixes a fresh X25519 key agreement with a fresh ML-KEM-768 encapsulation. Forward secrecy and break-in recovery therefore hold against a future quantum adversary as well as a present-day one.
- **Dual signatures** — every handshake and identity claim is signed twice: once with classical **Ed25519** and once with post-quantum **ML-DSA-65**, so authentication doesn't rest on a single cryptographic assumption.
- **Self-sovereign identity** — your `STADE-` ID is deterministically derived from the hash of your own public keys. No server issues it, no server can revoke it.
- **Contact verification** — verify a contact out-of-band to confirm you're really talking to them and not a man-in-the-middle.
- **Persistent ratchet sessions** — encrypted session state survives app restarts without ever weakening forward secrecy.

## Privacy & App Protection

- **PIN-protected vault** — while the app is locked the entire local database is held as a single authenticated ciphertext behind an Argon2id-derived key, with configurable auto-lock timing. While it is unlocked the database is readable on disk, so the vault is protection for a device at rest rather than against a compromised running system.
- **Duress PIN** — a second, separate PIN that instantly and permanently wipes the device if entered under coercion.
- **Scrambled PIN keypad** — randomizes key layout to defeat shoulder-surfing and smudge attacks.
- **Failed-attempt lockout** — escalating delays after repeated wrong PIN entries.
- **Screenshot & screen-recording blocking** — app content is hidden from screenshots and the OS recent-apps switcher.
- **Transport lock** — require your PIN before network/transport settings can even be viewed.
- **Tor identity wipe** — instantly discard and regenerate your Tor circuit identity.
- **Private notifications** — notifications can show "you have new messages" instead of sender names or message content.
- **Disappearing messages** — set a conversation to auto-delete its messages after a timer (30 minutes up to 1 day), synced automatically with the other side.
- **Launcher shortcut control** — choose whether recent conversations appear in the Android long-press app shortcut menu; deleted contacts are pruned automatically so nothing stale ever lingers there.

## Networking & Transport

- **Dual transport** — messages route over embedded **Tor** (onion services, no reliance on a system-installed Tor) or the **local network**, automatically or by your choice.
- **Bundled Tor binary** — Tor ships inside the app itself, per-device architecture, so there's no external dependency to install or trust.
- **Configurable Tor bridges**, including built-in obfs4 bridges, for use on censored or restrictive networks.
- **Resilient reconnection** — connections automatically retry with adaptive backoff, tuned to minimize battery drain while the app is backgrounded.
- **Tor-routed link previews** — link previews for URLs shared in chat are always fetched over Tor, regardless of which transport the conversation itself is using, and can be disabled entirely.

## Messaging

- One-on-one chats and multi-member **group chats**.
- **Stadiums** — a broadcast channel primitive: one owner posts, everyone who joins receives. Perfect for announcements or one-to-many updates without turning it into a group chat. Owners can rename, mute, or remove members and delete individual broadcasts or the whole stadium — all changes propagate peer-to-peer to every subscriber.
- **Voice messages** — record and send Opus-encoded voice clips directly in a conversation.
- **Stickers** — create your own from photos with built-in AI background removal, or bring over the packs you already have from other messaging apps.
- **Reactions** — react to any message with an emoji.
- **Delivery status** for every message.
- **Local full-text search** across your conversations.
- **Media attachments** with an in-app editor.

## Platforms

| Platform | Package format |
|---|---|
| Android | native APK |
| Windows | `.exe` installer |
| macOS | `.dmg` |
| Linux | `.deb`, `.rpm` |

## Verifying a download

Every Stade release published here is signed with the same key. Before installing an APK you downloaded from this repository, check that it carries our signature — a build that doesn't is not ours, no matter where you got it.

**Signing certificate SHA-256**

```
69:5D:86:C1:20:89:0F:EC:6B:C0:51:E7:33:A5:69:7B:B4:30:66:83:C2:51:DD:DA:59:B5:78:56:2F:07:82:62
```

Check an APK with `apksigner` from the Android SDK build-tools:

```
apksigner verify --print-certs Stade-<version>.apk
```

The `Signer #1 certificate SHA-256 digest` it prints must equal `695d86c120890fec6bc051e733a5697bb4306683c251ddda59b578562f078262`. The certificate subject is `CN=Stade, OU=Stade Development, O=Stade Development, L=Ankara, ST=Ankara, C=TR`.

If the fingerprint differs, or the APK is unsigned, do not install it.

> **Installing from Google Play?** Play re-signs every app with its own key, so a build installed from Play will show a different fingerprint than the one above. The fingerprint here applies to APKs downloaded directly from this repository.

## Localization

Stade is available in **English**, **Turkish**, **Korean**, **Persian**, and **Arabic**,
switchable from Settings. The interface lays itself out right-to-left for Persian and
Arabic.

## What Stade deliberately doesn't do

- **No accounts, no phone numbers, no central directory.** Every identity is self-issued and every message path is peer-to-peer.
- **No read receipts.** Whether and when you read a message is nobody else's business. Stade does show a contact as online while you are connected to them, and shows a status they chose for themselves, but never a last-seen time and never anything about what you have read.
- **No analytics, telemetry, or crash reporting.** Nothing about how you use Stade leaves your device.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build the project, what we expect in a pull request, and which changes need a discussion first.

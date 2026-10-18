# Contributing to Stade

Thanks for taking an interest in Stade. This file explains how to get the project
running, what we expect in a pull request, and which changes need a conversation
before you spend time on them.

## Everything is in English

Stade is developed in English. That means **code, identifiers, commit messages, pull
request titles and descriptions, and issues** — all of it in English, regardless of the
language you and the maintainers happen to share.

**Names that belong to Stade are never translated.** Stade, Stadium, Stadey, Paddy and
Radar are product names rather than descriptions, so they stay in Latin script in every
locale — neither translated nor transliterated. A Turkish reader sees `Stadium'a Katıl`
and an Arabic reader sees `الانضمام إلى Stadium`. Treat them the way you would treat any
other brand name inside a sentence.

The exceptions to English are the obvious ones: translated interface strings live in
the locale files, a language's own name stays in its own language, and test fixtures
may contain whatever text the test is actually about.

## Getting it building

You need **JDK 17**. For Android you also need the Android SDK, pointed at by a
`local.properties` file in the repository root:

```properties
sdk.dir=/path/to/Android/sdk
```

The first build downloads the Tor binaries that ship inside the app, one per
architecture. Expect around 400 MB on disk once unpacked, and expect it only once.
Every archive is checked against a pinned SHA-256 before it is unpacked, and the build
fails on a mismatch — so a download that does not match is a real problem, not
something to work around.

```bash
./gradlew :composeApp:compileKotlinDesktop        # desktop
./gradlew :composeApp:compileDebugKotlinAndroid   # Android
./gradlew :composeApp:run                         # run the desktop app
./gradlew :composeApp:assembleDebug               # debug APK
```

Release builds are signed with a key the maintainers hold, so `assembleRelease` will
not produce a publishable build for you. That is expected; you do not need it.

## Tests

```bash
./gradlew :composeApp:desktopTest
```

This is what CI runs on every pull request, so run it before you push. The suite is
plain JVM tests with no emulator or device involved.

If you are fixing a bug, **add a test that fails without your fix**. A test that passes
either way still has value as a guard, but say so in the description rather than
implying it reproduces the problem — we would rather know.

Anything that can only be checked by hand (an animation, a file picker, a fingerprint
prompt) should say so explicitly, along with what you did check.

## Before you open a pull request

- **Both targets have to compile.** Most of the code is shared, and it is easy to
  change common code in a way that only breaks one platform.
- **Keep it to one subject.** A bug fix and a refactor in the same pull request take
  much longer to review than two pull requests.
- **Match the surrounding code.** Naming, formatting, and structure should look like
  the file you are editing.
- **No code comments.** This is deliberate and near-universal in the codebase: one file
  out of three hundred has any. Prefer a well-named function or variable over a comment
  explaining an unnamed one. If something genuinely cannot be made self-explanatory,
  put the explanation in the pull request description, where reviewers will read it and
  it cannot drift out of date.

## Adding or changing interface text

Never hard-code text that a user will see. Every string goes through the localization
layer:

1. Declare it in `AppStrings.kt`.
2. Add the English text in `EnglishStrings.kt`.
3. Add it to **every** other locale file in the same directory. The build will not
   compile until all of them have it.

The `i18n/*.json` files are generated from the Kotlin sources by a maintainer tool that
is not in this repository. **Do not edit them by hand** — change the `.kt` files and
leave the JSON alone; a maintainer will regenerate it.

If you cannot translate into a language, a machine translation clearly flagged in the
pull request description is fine. We would rather know it needs review than discover it
later.

## Changes that need a discussion first

Open an issue before writing code if your change touches any of these. It is not
bureaucracy — these areas are load-bearing for the guarantees the app makes, and a
change here can be correct code that we still cannot take.

- **Cryptography** — the handshake, the ratchet, key derivation, the vault.
- **Transport** — Tor, the local network, connection management, the outbox. Much of
  this cannot be verified without two real devices, so unverifiable changes here are
  held to a higher bar.
- **Anything that contacts a server.** Stade has none, and a new outbound endpoint is a
  change to the threat model rather than a feature.
- **New dependencies**, especially ones with their own network behaviour.
- **Database schema.** Existing installs are migrated in place; a change that only
  works on a clean install will lose people their accounts.

## What we will not merge

Some things are deliberately absent, and a pull request adding them will be declined
however well it is written:

- **Analytics, telemetry, or crash reporting that leaves the device.**
- **Read receipts, "seen" indicators, or online-presence features.** They leak the
  recipient's behaviour, which is exactly what the app exists to avoid. The profile
  status in the app is picked by hand and never set from activity, which is why it
  stays.
- **Accounts, phone numbers, or any central directory.**
- **Live audio or video calls.** Tor is TCP-only and high-latency; carrying real-time
  media would mean routing around it.

If you think one of these deserves reconsidering, open an issue and make the case
there. Do not open it as a pull request.

## Reporting a security problem

Do not open a public issue for a vulnerability. Contact us privately and
give us a chance to ship a fix first.

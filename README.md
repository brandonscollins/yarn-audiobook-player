# Yarn Audiobook Player

A personal Android audiobook client for Plex. Your books stay on your own
server; Yarn is just a good player for them. Built because the great Android
players are local-only and the Plex-native options have unreliable players.

**Status: v1.0, working and in daily use.** Streaming, progress that survives
anything, playback speed, a sleep timer that arms itself at bedtime, EQ and
volume boost, collections, sorting, and search all work. Offline downloads are
the next milestone — see [next_steps.md](next_steps.md).

## What it does

- **Streams** straight from Plex — no downloading required, no ports opened,
  works from anywhere Plex works (LAN, remote, or relay, chosen automatically
  by racing every candidate connection)
- **Never loses your place**, including across force-stops and dead networks
- **Sleep timer that thinks**: set a bedtime window, and any time you press
  play inside it — on screen or on your headphones — a fresh timer arms
  itself. It fades out rather than cutting, and rewinds to where the fade
  began, so the half-heard sentences aren't lost
- **Sound control**: 0.5–3.0× speed, volume boost up to +12 dB for quiet
  narrators, and your device's equalizer with presets or manual bands
- **A library you can navigate**: grid or list, your Plex collections and
  series, sort by title / recently added / publication date, an A-Z rail, and
  instant offline search

## Building

Headless — no Android Studio required. See "Building on this Mac" in
[CLAUDE.md](CLAUDE.md) for the toolchain, then:

```
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/yarn-1.0-debug.apk`. Install it
with `adb install -r <apk>`; `-r` preserves your login and saved positions
(the database migrates in place).

## Why "Yarn"

A yarn is a story told aloud — the only word that's a book word and an audio
word at the same time.

## The one rule

**Position is never lost.** Not after a call, a bluetooth drop, or the app
being killed overnight. Local storage is the source of truth; Plex is backup
and cross-device sync. Everything else in this app is allowed to be lazy;
this is not.

## Credits & license

GPL-3.0. The Plex API integration approach is learned from (and in places
derived from) [Chronicle](https://github.com/mattttvaughn/chronicle) by Matt
Vaughn, GPL-3.0.

The bundled display face is [Lora](https://fonts.google.com/specimen/Lora) by
Cyreal, SIL Open Font License 1.1 — full text in
[LORA-OFL.txt](LORA-OFL.txt).

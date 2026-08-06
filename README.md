# Yarn Audiobook Player

A personal Android audiobook client for Plex. Stream from your own server or
download for offline — same book, same player, same position. Built because
the great Android players are local-only and the Plex-native options have
unreliable players.

**Status:** pre-code. PRD and architecture settled; scaffold is the next step.
See [next_steps.md](next_steps.md).

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

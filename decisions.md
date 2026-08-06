# Decisions (ADR log)

Newest last. One entry per decision that would be expensive to reverse or
confusing to rediscover.

## ADR-001 — Greenfield app on Plex, not Audiobookshelf, not a Chronicle fork (2026-08-06)

**Decision:** Build a new Android client against the existing Plex server.
**Why:** Audiobookshelf means another server to host/update/expose — rejected
on ops burden. Plex already does metadata, auth, and portless remote access.
Chronicle (the existing Plex audiobook app) is GPL-3.0 and open source, but
its codebase is 2020-era (XML views, Dagger, old ExoPlayer); renovating it
costs about as much as a clean build once its Plex API knowledge is extracted.
**Consequence:** We maintain a player, not a server. Unofficial Plex API risk
is accepted (historically stable; personal app absorbs occasional breakage).

## ADR-002 — Stack: Kotlin/Compose/Media3/Retrofit/Room/WorkManager, single module, no DI framework (2026-08-06)

**Decision:** Standard modern Android stack, minimum structure.
**Why:** Media3's MediaSessionService alone provides notification player,
bluetooth controls, and most of Android Auto. Solo app doesn't need Hilt or
modules. Add structure when it hurts, not before.

## ADR-003 — GPL-3.0, public from day one (2026-08-06)

**Decision:** License GPL-3.0; repo public immediately.
**Why:** We derive from Chronicle (GPL-3.0), so GPL is the compliant choice,
and public-from-day-one means nothing to remember at release time. Nothing
sensitive lives in the repo (tokens live on-device only).

## ADR-004 — Name: Yarn Audiobook Player (2026-08-06)

**Decision:** "Yarn" — a story told aloud; the word that is simultaneously a
book word and an audio word. Repo: `brandonscollins/yarn-audiobook-player`.
Package: `io.github.brandonscollins.yarn`.

## ADR-005 — Position ledger: local-first, Plex as backup (2026-08-06)

**Decision:** Playback position writes to Room synchronously on every
pause/interruption/track-change/audio-focus-loss; a WorkManager outbox syncs
to Plex with retry; furthest-ahead wins on conflict.
**Why:** "Never lose my place" is the app's entire quality bar; server sync
is inherently best-effort, so it can't be the source of truth.

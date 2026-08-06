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

## ADR-006 — Identity: paper-and-ink palette, no dynamic color (2026-08-06)

**Decision:** A fixed branded palette in both modes — warm cream paper
(`#FAF5EA`) with near-black ink (`#1C1714`) in light, warm brown-black
(`#17120E`) with cream (`#F2E7D2`) in dark, and ONE antique gold (`#B8801C`)
shared by both. Dark is unconditional default (PRD), not `isSystemInDarkTheme()`.
Serif (Lora, one variable `.ttf` in `res/font/`) on display/headline/title;
platform sans stays on body/label. Shape scale 8/12/16/24/32 dp.
**Why:** Material You dynamic color made Yarn look like every other Material
app (specifically: like Chronicle). The gold is a single value because
`#B8801C` is the one that clears 3:1 against cream *and* 4.5:1 against the dark
page; gold is for fills, indicators and large text only, ink/cream for small
text. `PaletteContrastTest` locks those ratios so a later "nicer gold" can't
quietly break legibility.
**Consequence:** Two sanctioned dependencies added — `material-icons-extended`
(real Pause/Replay30/Forward30/Bedtime/GraphicEq/Sort glyphs, retiring the
hand-drawn `PauseGlyph` and the "-30"/"+30" text buttons) and the font resource.
Nothing else. Icons-extended is a big artifact and the debug APK is unminified
(~70 MB); turning on R8 for release will strip the unused glyphs when release
builds start mattering.

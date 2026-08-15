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

## ADR-007 — Launcher icon and artifact naming; v1.0 (2026-08-06)

**Decision:** The mark is a gold skein of yarn low-left on the ink page, one
cream thread unwinding up-right and resolving into three broadcast arcs — a
story told aloud, which is what the name means. Authored entirely as vector
XML (adaptive foreground + background + a monochrome variant for Android 13+
themed icons); no PNG densities, since minSdk 29 means every device supports
adaptive icons. Palette is ADR-006's, unchanged. Version is **1.0** and the
artifact is named from it (`base.archivesName`), so builds land as
`yarn-1.0-debug.apk` rather than `app-debug.apk`.
**Why:** The app went into daily use with no icon at all (the standing
`MissingApplicationIcon` lint warning) and a default artifact name that read
as throwaway. The skein-plus-wave is the one image that says both "book" and
"audio" without a headphone cliché.
**Consequence:** `versionName` and the artifact name share one `appVersionName`
constant in `app/build.gradle.kts` — bump that single value, not two.

## ADR-008 — `finished` is a durable column, not the outbox flag (2026-08-12)

**Decision:** `playback_positions` gains a `finished` column (schema v4,
`MIGRATION_3_4`) alongside the existing `finishedPending`. `finishedPending` stays
what it always was — "Plex hasn't been told yet", cleared by the outbox on a
successful `/:/scrobble`. One DAO method, `setFinished(bookId, finished)`, sets
both and clears `syncedToPlex` so the outbox re-examines an already-synced row;
auto-finish and a manual "mark finished" go through it.
**Why:** The UI needs to know a book is done, and the only existing signal was
erased by the very sync that confirmed it. Plex's own 90% rule is still
deliberately defeated (gotcha #2), so nothing else can tell us.
**Consequence:** `PositionLedger.record` carries `finished` forward on every
upsert, like `finishedPending`. `markSynced` clears only the outbox flag.

## ADR-009 — the Plex `viewOffset` mirror is consumed once, at book open (2026-08-12)

**Decision:** `PlayerController.playBook` resolves the furthest-ahead conflict in
`resumePoint`, writes the winner into the ledger, and then zeroes the book's
`Track.viewOffsetMs`. Ledger write first, mirror clear second, so a kill between
the two loses nothing.
**Why:** Nothing local ever wrote that mirror — only a sync from Plex does — so a
mirror left set kept winning every later comparison. Harmless while playback only
moved forward; rewind-on-resume moves it backwards, and the stale
mirror silently undid the rewind, snapping the listener forward to the old offset
on the next resume until playback passed it. A manual 30s rewind before a pause
had the same latent bug.
**Consequence:** Cross-device progress still wins on open, because the mirror is
read before it is cleared. "Mark as unplayed" already zeroed the same mirror for
the same reason.

## ADR-010 — Downloads are plain public files via MediaStore, not app-private cache (2026-08-14)

**Decision:** `DownloadWorker` (one unique WorkManager job per book,
`download_book_<id>`, KEEP) streams each track to
`Music/Yarn/<Book Title>/NN - Track Title.ext` through MediaStore
(`VOLUME_EXTERNAL_PRIMARY`, `IS_PENDING` while writing), using the part URL
with `?download=1` and the token as a query param — same style as streaming,
no custom DataSource. `Track.localUri` (schema v6) stores the `content://`
URI; it and `isCached` are set per track as each lands, so a retry or cancel
resumes instead of restarting. The player prefers the local URI when it still
resolves, silently clearing the columns and falling back to streaming when a
file manager deleted the file. Remove deletes the MediaStore rows and clears
the columns; the position ledger is never touched by any of it.
**Why:** The explicit requirement was files other apps can see and manage —
that rules out `filesDir`. On API 29+ MediaStore inserts into `Music/` need no
storage permission.
**Consequence:** MediaStore ownership is per *install*: after a reinstall the
app can no longer delete its old rows, so "Remove download" leaves orphans a
file manager must clean up. Accepted for a personal sideloaded app (noted as
a `ponytail:` ceiling in `DownloadWorker`; upgrade path is SAF or
`MANAGE_EXTERNAL_STORAGE`). A fully downloaded book also skips the connection
race at open, so offline playback starts immediately.

## ADR-011 — Chapters come from Plex `includeChapters=1`, cached in Room, fetched lazily (2026-08-14)

**Decision:** Embedded chapters are asked of Plex before any local parsing:
`GET /library/metadata/{trackId}?includeChapters=1` returns each track's
`Chapter` elements, which `LibrarySyncRepo.syncChapters` maps into a `chapters`
table (schema v7, `MIGRATION_6_7`, PK trackId+index, `startMs` is the offset
within the track's own file). The fetch is lazy — `playBook` runs it, after
`play()`, only when the book has no chapter rows — never from the regular
library sync, which would cost N extra requests per book. The player merges
rows with the track list into book-level absolute offsets (`bookChapters` in
`player/Chapters.kt`); ticks and the Chapters sheet use those when present and
fall back to track boundaries exactly as before. Blank Plex titles become
"Chapter N".
**Why:** Single-file books (one big MP3 or M4B) had no ticks and a one-row
Chapters sheet, and the server often already knows the chapters — a model field
and one endpoint beat writing ID3/`chpl` parsers.
**Consequence:** Books whose files carry chapters Plex doesn't expose still
show track boundaries; ID3 `CHAP` and M4B `chpl` parsing stay on Milestone 5
as the remaining sub-paths. A genuinely chapterless book re-probes on each
open (a handful of cheap requests, silently dropped offline).

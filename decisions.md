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

## ADR-012 — The debug keystore is the permanent signing identity, backed up (2026-08-15)

**Decision:** Rather than generate a release keystore, the machine-generated
`~/.android/debug.keystore` that has signed every installed build is promoted
to the app's permanent signing identity: copied (fingerprint-verified) to
OneDrive `Claude/cli-sync/secrets/yarn-signing.keystore` and to the gitignored
repo path `keystore/yarn-signing.keystore`, and wired into both build types via
`keystore.properties` (also gitignored; standard debug credentials). A fresh
clone without the properties file still builds with default debug signing.
`versionCode` now derives from `appVersionName` (major·10000 + minor·100 +
patch, so 1.2 → 10200), so the installer sees every version bump as an upgrade.
**Why:** A new key would force one uninstall — wiping Room and the position
ledger, the exact loss the change guards against. Keeping the existing key
means every future build, from any machine holding the backup, updates in
place. The key never enters the public repo.
**Consequence:** The signing identity has debug-standard passwords; acceptable
for a personal sideloaded app whose threat model is key *loss*, not key theft.

## ADR-013 — Streaming URLs resolve per request; a playback error re-races (2026-08-15)

**Decision:** The player's media source factory wraps the default one in
`ResolvingDataSource`: any http(s) request whose path contains
`/library/parts/` gets its scheme+authority rewritten to the *current*
`chosenServerUri` at load time (path, query and token untouched;
`content://` downloads pass through). On a network-class `PlaybackException`
(codes 2000–2999) for a streaming item, the service runs one single-flight
connection race (`PlexConnectionManager.connect`) and, on a win, `prepare()` +
`play()` — resuming from the stall point. A losing race leaves the normal
error state; the ledger already holds the position.
**Why:** URIs were built once at `playBook`, so a LAN→remote move mid-session
left the queue pointing at a dead base URL until the book was reopened.
**Consequence:** No retry loop — one race per error event. Books playing from
downloads never trigger any of this.

## ADR-014 — ID3 `CHAP` fallback when Plex returns no chapters (2026-08-15)

**Decision:** When `syncChapters`' Plex pass yields zero chapters for a book,
media3's `MetadataRetriever` reads each track's embedded ID3 `ChapterFrame`s
directly (from the downloaded file when present, else the stream URL; ~30s
per-track timeout), mapped into the same `chapters` table — titles from the
frame's `TIT2` sub-frame, else "Chapter N". Fewer than two chapters across the
whole book counts as none: a single marker is just "the file starts".
`ChapterTocFrame` is ignored; ordering is by start time. Plex chapters, when
present, always win.
**Why:** Plex indexes most embedded chapters but not all; the frames are in
the files regardless, and media3 already decodes them — only the plumbing was
missing. M4B `chpl` parsing remains the one unbuilt sub-path (needs a real
parser).
**Consequence:** A chapterless book's open now costs a metadata probe per
track after the Plex probe; both are silently dropped offline and re-tried
next open.

## ADR-015 — "Mark unplayed" is an outbox tombstone drained by `/:/unscrobble` (2026-08-15)

**Decision:** Marking a book unplayed writes one tombstone ledger row
(`positionMs = 0`, `unplayedPending = true`, schema v8) and enqueues the sync
worker, which calls `/:/unscrobble` with the *book's* ratingKey (Plex cascades
album→tracks, one call clears the whole book) and then CAS-deletes the row.
Every "started" consumer filters tombstones via `isStartedRow`, so the book
reads as Not started immediately. Playing the book before the drain overwrites
the tombstone — deliberately dropping the pending unscrobble, since a reported
position supersedes it.
**Why:** The old best-effort timeline sweep at `time=0` was lost if the server
was unreachable; the next `syncTracks` resurrected the server's old progress.
The outbox already existed for `finishedPending` — this mirrors it exactly.
**Consequence:** "Not started" now formally means "no ledger row, or only a
tombstone". `PositionLedger.record` deliberately does not preserve the flag.

## ADR-016 — Android Auto via `MediaLibraryService`, resolution service-side (2026-08-15)

**Decision:** `PlaybackService` is now a `MediaLibraryService` (a
`MediaSessionService` subclass, so the phone UI's `MediaController` flow is
unchanged). The browse tree is two folders — Continue listening (recent ledger
rows, ≤20) and Library (alphabetical, ≤100) — of playable leaves with
`mediaId = "book/<id>"`. Picking one resolves server-side: tracks from Room,
`resumePoint` plus the rewind-on-resume math, and per-track MediaItems
mirroring `PlayerController.toMediaItem` field-for-field (duplicated with a
pointer comment — both are private to their owners). The Plex viewOffset
mirror is not consumed on the Auto path; the ledger stays primary and
furthest-ahead wins at next app open. Artwork uses the same transcoder URL as
the app's `CoverArt`.
**Why:** The session work was already correct; Auto needed a browse surface
and a way to start a book without the app's UI in the loop.
**Consequence:** Unverified against a real head unit — book taps are handled
in both `onSetMediaItems` and `onAddMediaItems` because media3 1.4.1's mapping
from legacy `playFromMediaId` is undocumented; folder ordering and artwork
rendering need an on-device check. With no raced connection at browse time,
covers are simply absent.

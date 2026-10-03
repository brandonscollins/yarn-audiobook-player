# Next steps

Current state: **v1.4, working and in daily use.** P0 was verified on a real
phone against the real Plex server; the batches below shipped on top of it.
Build with `./gradlew assembleDebug` → `app/build/outputs/apk/debug/yarn-1.4-debug.apk`.

## Shipped (2026-10-03) — v1.4: self-update from the Brasco App Store

Yarn now updates itself: on resume (at most every 6 hours) it asks the Brasco
App Store for `latest.json`, and when a newer build is published Home shows a
card with Later / Install (ADR-018). Install downloads, checks size and
SHA-256, and opens Android's own update prompt. Signing now works on any of
Brandon's machines via the OneDrive-synced keystore (ADR-017), so a build from
the Windows PC updates the installed app in place. Not yet verified on the
phone: the first real update through the store (install 1.4 by hand, then
publish a later build and update from the card).

## Shipped (2026-08-06)

**P0 — the app works.** Plex PIN login, server discovery with the connection
race, library + collections sync, streaming with resume, the position ledger
(local-first, 7 write-triggers, furthest-ahead conflict rule), progress sync
to Plex, playback speed, sleep timer with fade + rewind, the auto sleep
window, and all the screens. Two adversarial review passes.

**P1 batch.** Volume boost (0–12 dB) and device EQ (presets + manual bands)
with their own sheet; continuous 0.5–3.0× speed with the reference-image
speed sheet; library view modes (grid / list / compact list) with sort by
title, recently added, or recently published; the A-Z fast-scroll rail;
local search over the Room cache; the paper-and-ink design identity
(ADR-006); the launcher icon (ADR-007).

## Next up, roughly in order

**Milestone 4 — Downloads.** The biggest remaining feature, and the one that
pairs with the deferred reconnect work below (same plumbing).

- [x] WorkManager download of a book's tracks (`?download=1` + token as query
      param) — `DownloadWorker`, one unique job per book, stored as plain
      audio files in public `Music/Yarn/<Book Title>/` via MediaStore (ADR-010)
- [x] Player source resolution: local file if present (verified to still
      exist at book open, falling back to streaming if not), else stream
- [x] Download state badges in library + Download / Cancel / Remove in the
      book detail overflow menu (`Track.localUri` is schema v6; both cached
      columns are carried across syncs)
- [x] Mid-session reconnect — **done (2026-08-15, ADR-013).**
      `ResolvingDataSource` rewrites every `/library/parts/` request to the
      current `chosenServerUri`; a network-class player error triggers one
      single-flight re-race and resumes in place.

**Milestone 5 — Remaining player polish.**

- [ ] **Chapter compatibility — make ticks work for every book shape.** Today
      ticks come from track boundaries only, which covers multi-file books and
      leaves single-file books (one big MP3, or an M4B) with none. Goal is
      chapters wherever the data exists, in rough order of payoff:
      1. [x] Ask Plex first — **done (2026-08-14, ADR-011).** Per-track
         metadata with `includeChapters=1` exposes any `Chapter` elements the
         server extracted; cached in the `chapters` table (schema v7), fetched
         lazily by `playBook` when a book has no rows, driving ticks and the
         Chapters sheet with track boundaries as the fallback. The two paths
         below remain for books where Plex itself has no chapter data.
      2. [x] ID3 `CHAP` for MP3 — **done (2026-08-15, ADR-014).** When the
         Plex pass finds zero chapters, a one-off `MetadataRetriever` pass
         reads the files' `ChapterFrame`s (local file when downloaded, else
         the stream) into the same `chapters` table; <2 chapters counts as
         none. `CTOC` ignored — start-time order is enough.
      3. M4B / MP4 `chpl` + the QuickTime chapter text track. media3 parses
         neither; this is the only part that means writing a real parser.
      All paths feed the same `chapters` table and the same tick/sheet UI.
- [x] Android Auto — **built (2026-08-15, ADR-016), not yet verified in a
      car.** `MediaLibraryService` with Continue listening + Library folders;
      picking a book resolves tracks + resume point service-side. Check on
      the real head unit: does tapping a book play it from the right spot,
      and does artwork render.
- [ ] Listening stats (needs a local play-time log — the only part of the old
      "recents / finished / stats" line still outstanding)

**Parking lot.** Bookmarks with notes, skip silence.

## Shipped (2026-08-12) — the UX batch

Home: "Recently played" shelf (5, structurally excluding the Continue
listening book) plus a `RECENTLY_PLAYED` view-all screen; time remaining
(`formatRemaining`) on the cards and in the mini player; a "Finished" shelf;
"Up next" from the next unstarted book in the current book's collection.
Player: book-level scrub bar with chapter ticks at track boundaries;
rewind-on-resume (off / fixed / smart, smart = pause ÷ 10 capped at 60s, 10s
deadband); per-book speed memory; "end of chapter" sleep option.
Library/detail: mark finished / unplayed, All / In progress / Not started /
Finished filter chips, tappable author. Plus a "Resume" launcher shortcut.

Schema went 3 → 5: `playback_positions.finished` (ADR-008) and
`book_collection_cross_ref.ordinal`, both additive migrations.

## Known deferred issues (deliberate, from the review passes)

- ~~"Mark as unplayed" timeline sweep~~ — fixed 2026-08-15 (ADR-015): real
  `/:/unscrobble` drained by the outbox via an `unplayedPending` tombstone.
- ~~`EffectsState.bandInfo` stale after service release~~ — fixed 2026-08-15:
  `AudioEffects.release()` clears it.
- Per-book speed keys in `PlayerPrefs` are never pruned, so a removed book
  leaves its key behind.
- A book in several collections answers "Up next" from whichever peer list
  comes first; since 2026-08-15 that only matters as the *last* rung of
  `nextUpNext` — title numbering (SeriesOrder) answers first.
- `onDestroy`'s ledger write is async — a hard kill immediately after can
  drop it. Bounded by the 10s tick and the pause write; the alternative is
  `runBlocking` on the main thread, which trades a rare loss for an ANR.
- Effect setters are silently dropped if the `MediaController` isn't
  connected. Unreachable today (the controller connects at NavHost creation);
  matters only if the EQ sheet becomes reachable from Settings.
- `AudioEffects.setBand` doesn't re-baseline off an active device preset —
  only reachable via prefs written by an older build.
- `LibraryViewModel.rows()` runs one Room query per in-progress book on every
  emission. Bounded by books-with-a-position; a JOIN would remove it.
- Per-row play state in `ProgressSyncWorker` (cosmetic on the Plex dashboard).
- No Android-dependent test harness (Robolectric/fakes). Add when a
  regression actually bites; the pure logic is unit-tested (94 tests at v1.4).
- Full `lint` exits non-zero on known pre-existing issues (37 errors at v1.4):
  `WrongConstant` from media3's incomplete `SessionResult`/`LibraryResult`
  IntDefs, `ProduceStateDoesNotAssignValue` where lint can't see through a
  nested `let`/`collect`, `UnsafeOptInUsageError` in `Id3Chapters.kt`, and
  `MissingIntentFilterForMediaSearch` (Auto voice search). Lint is not a gate;
  `lintVitalRelease` (run by `assembleRelease`) passes.

## If this ever becomes a release build

Signing is settled: the promoted debug key signs debug and release on every
machine (ADR-012, ADR-017). What remains is `isMinifyEnabled = true` on the
release build type; R8 will strip the unused `material-icons-extended` glyphs,
which is most of the current ~66 MB APK.

## Making updates less janky — done (2026-10-03, ADR-018)

Superseded by the Brasco App Store. The keystore and `versionCode`
prerequisites were met in v1.3 (ADR-012), signing now works on every machine
(ADR-017), and Yarn's in-app checker pulls from the store instead of GitHub
releases. Android still shows its own install confirmation, so the ceiling
remains "check → download → tap Install", never a silent overnight update.

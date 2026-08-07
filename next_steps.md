# Next steps

Current state: **v1.0, working and in daily use.** P0 was verified on a real
phone against the real Plex server; the P1 batch below shipped on top of it.
Build with `./gradlew assembleDebug` → `app/build/outputs/apk/debug/yarn-1.0-debug.apk`.

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

- [ ] WorkManager download of a book's tracks (`?download=1` + token header)
- [ ] Player source resolution: local file if present, else stream
- [ ] Download state badges in library (the `isCached` column already exists
      on both `Audiobook` and `Track` and is carried across syncs)
- [ ] Do the mid-session reconnect with it: a `DataSource.Factory` resolving
      the base URL per request, plus a re-race on `IOException`. Today a
      LAN→relay move mid-session leaves the queue pointing at a dead URI,
      because `PlayerController` builds MediaItem URIs once at `playBook`.

**Milestone 5 — Remaining player polish.**

- [ ] Rewind-on-resume: fixed (1 min) or smart (scaled by pause length).
      Never built; it's in the PRD's P1 table.
- [ ] Android Auto (mostly config now that `MediaSessionService` is correct)
- [ ] Recents / finished shelf / listening stats (Plex `lastViewedAt` and
      view counts are already synced; stats need a local play-time log)
- [ ] Chapter ticks on the scrub bar

**Parking lot.** Bookmarks with notes, per-book speed memory, skip silence.

## Known deferred issues (deliberate, from the review passes)

- `onDestroy`'s ledger write is async — a hard kill immediately after can
  drop it. Bounded by the 10s tick and the pause write; the alternative is
  `runBlocking` on the main thread, which trades a rare loss for an ANR.
- `EffectsState.bandInfo` isn't cleared on service release, so a destroyed
  service leaves stale band info on screen. Harmless single-device.
- Effect setters are silently dropped if the `MediaController` isn't
  connected. Unreachable today (the controller connects at NavHost creation);
  matters only if the EQ sheet becomes reachable from Settings.
- `AudioEffects.setBand` doesn't re-baseline off an active device preset —
  only reachable via prefs written by an older build.
- `LibraryViewModel.rows()` runs one Room query per in-progress book on every
  emission. Bounded by books-with-a-position; a JOIN would remove it.
- Per-row play state in `ProgressSyncWorker` (cosmetic on the Plex dashboard).
- No Android-dependent test harness (Robolectric/fakes). Add when a
  regression actually bites; the pure logic is unit-tested (27 tests).
- Lint exits non-zero on 4 known false positives: one `WrongConstant` from
  media3's incomplete `SessionResult` IntDef, three
  `ProduceStateDoesNotAssignValue` where lint can't see through a nested
  `let`/`collect`. Lint is not a gate.

## If this ever becomes a release build

Debug-signed is fine for sideloading and is what's shipping today. A real
release build needs: a keystore **you** generate (`keytool -genkey -v
-keystore yarn.jks ...` — the password is yours, put it in
`keystore.properties`, which is already gitignored), a `signingConfigs` block
reading that file, and `isMinifyEnabled = true` on the release build type.
R8 will also strip the unused `material-icons-extended` glyphs, which is most
of the current ~66 MB APK.

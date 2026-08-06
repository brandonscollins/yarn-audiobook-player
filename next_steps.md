# Next steps

## P0 push — DONE (2026-08-06, one session, 5 agent phases)

Milestones 0–3 of the original plan are built and unit-tested (13 tests):
toolchain, scaffold + contract layer, Plex engine (PIN auth, connection
race, library/collections sync, progress sync worker), playback engine
(MediaSessionService, position ledger with 7 write-triggers, resume
furthest-ahead rule, speed, sleep timer with fade+rewind, auto sleep
window incl. midnight crossing), Compose UI (onboarding, Home, Library,
Book detail, Player, mini-player, Settings), adversarial review pass.

## NOW: first real-device verification (Brandon)

- [ ] Sideload `app/build/outputs/apk/debug/app-debug.apk` on the phone
- [ ] Full journey: PIN login → server pick → library pick → sync → stream a book
- [ ] Kill-test the invariant: play, force-stop the app, reopen → position kept?
- [ ] Overnight test: does the auto sleep window arm at bedtime? Bluetooth
      headphone play-button re-arm?
- [ ] Check Plex web dashboard shows progress moving

## Deferred from review (deliberate — see review commits dbc4e94+)

- [ ] Mid-session reconnect (LAN→relay while playing): per-request base-URL
      DataSource.Factory + re-race on IOException. Do together with downloads
      (M4) — same factory.
- [ ] onDestroy ledger write is async (bounded by 10s tick; runBlocking
      trade-off rejected for now)
- [ ] Per-row play state in ProgressSyncWorker (cosmetic on Plex dashboard)
- [ ] Test harness for Android-dependent logic (Robolectric or fakes) — add
      when a regression actually bites

## Milestone 4 — Downloads (P1)

- [ ] WorkManager download of a book's tracks (`?download=1`)
- [ ] Player source resolution: local file if present, else stream
- [ ] Download state badges in library

## Design refresh — DONE (2026-08-06)

Paper-and-ink identity: branded palette both modes (dark default, no dynamic
color), Lora serif for titles/numerals, rounded shape scale, real icons, speed
sheet and EQ/boost sheet, polish pass over every screen. See ADR-006.

## Milestone 5 — Polish (P1/P2)

- [ ] Rewind-on-resume (fixed / smart) — P1, not yet built
- [x] Volume boost + EQ — engine done (`AudioEffects.kt`: LoudnessEnhancer 0–1200 mB,
      Equalizer presets + manual bands, re-attach on audio-session change). Continuous
      speed 0.5–3.0 in the engine too. Controls come with the design phase; the facade
      surface is on `PlayerController` (boostMb / eqEnabled / eqPreset / eqBandLevels /
      eqBandInfo + setters). Controls shipped in the design phase: speed sheet and
      EQ/boost sheet off the Player screen.
- [ ] Android Auto
- [ ] Search (local-first)
- [ ] Recents / finished shelf / stats
- [x] Material icons for pause/±30s (`material-icons-extended` added in the
      design phase; `PauseIcon.kt` deleted)

## Parking lot

- Bookmarks with notes, per-book speed memory, skip silence
- Tailscale for the broader multi-site network (unrelated to Yarn — Plex
  relay covers this app; free tier is 100 devices / 3 users, subnet router
  covers whole LANs)

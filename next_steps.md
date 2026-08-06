# Next steps

Working top-down. Each milestone ends with something runnable on a phone.

## Milestone 0 — Scaffold (next)

- [ ] Android Studio project: empty Compose activity, package
      `io.github.brandonscollins.yarn`, minSdk 29, targetSdk latest
- [ ] Gradle deps: Media3, Retrofit + kotlinx.serialization, Room, WorkManager
- [ ] Decide dev machine (Windows box with Android Studio? Mac?) and note it here
- [ ] `.gitignore` sanity check after Studio generates its files

## Milestone 1 — Sign in and see books

- [ ] Plex PIN auth flow (port from Chronicle's `PlexLoginRepo`)
- [ ] OkHttp interceptor with X-Plex headers + token (port `PlexInterceptor`)
- [ ] Server discovery + connection race (port `PlexConfig.chooseViableConnections`)
- [ ] Library + collections fetch into Room cache
- [ ] Ugly-but-working library grid

## Milestone 2 — Stream one book, never lose position

- [ ] MediaSessionService + ExoPlayer, stream MP3 track by URL + token
- [ ] Position ledger in Room (write on every pause/interruption/focus-loss)
- [ ] playQueues session + timeline progress sync via WorkManager outbox
      (remember the duration*2 gotcha — CLAUDE.md #2)
- [ ] Resume from Home screen in one tap

## Milestone 3 — Player features

- [ ] Playback speed
- [ ] Sleep timer with fade + rewind-to-fade-start
- [ ] Auto sleep window (arm on play within window; chip with cancel)
- [ ] Rewind-on-resume (fixed / smart)

## Milestone 4 — Downloads

- [ ] WorkManager download of a book's tracks (`?download=1`)
- [ ] Player source resolution: local file if present, else stream
- [ ] Download state badges in library

## Milestone 5 — Polish

- [ ] Volume boost + EQ
- [ ] Android Auto
- [ ] Search (local-first)
- [ ] Recents / finished / stats

## Parking lot

- Bookmarks with notes, per-book speed memory, skip silence
- Tailscale for the broader multi-site network (unrelated to Yarn — Plex
  relay covers this app; free tier is 100 devices / 3 users, subnet router
  covers whole LANs)

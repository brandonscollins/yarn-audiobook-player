# Yarn — operating manual

Personal Android audiobook client for Plex. One user (Brandon), sideloaded,
GPL-3.0. Read PRD.md for scope, decisions.md for the ADR log, next_steps.md
for current work.

## Stack (decided — don't relitigate)

- Kotlin + Jetpack Compose, Material 3
- Media3: ExoPlayer + `MediaSessionService` (notification, bluetooth,
  Android Auto all hang off this one service)
- Retrofit + kotlinx.serialization; Plex returns JSON with
  `Accept: application/json`
- Room: library cache + position ledger + progress outbox
- WorkManager: downloads, progress sync retry
- Single module. No DI framework — constructor-passed dependencies. Add
  structure only when it hurts.

## Reference codebase

Chronicle (GPL-3.0) is cloned at `~/code/chronicle-reference` (Mac). Its
Plex layer is ~1,700 lines at
`app/src/main/java/io/github/mattpvaughn/chronicle/data/sources/plex/`.
Copy what's timeless (endpoint shapes, auth flow, model fields, gotcha
workarounds); skip what's dated (Dagger, XML views, LiveData, Fetch2).
Derived code is fine — this repo is GPL-3.0 for exactly that reason. Credit
in README stays.

## Plex API gotchas (each cost Chronicle real pain — don't rediscover)

1. **Timeline progress updates silently no-op** unless a session was opened
   first via `POST /playQueues`.
2. **Plex auto-marks items finished at 90%** — wrong for audiobooks with
   credits. Workaround: report `duration = actualDuration * 2` in timeline
   calls so the threshold never trips; mark finished explicitly via
   `/:/scrobble` when *we* decide the book is done.
3. **Connection selection is a race, not a lookup:** get all connection
   candidates from `/api/v2/resources?includeHttps=1&includeRelay=1`, probe
   `{uri}/identity` on all of them concurrently (local-first ordering),
   first success wins, ~15s timeout. This is what makes the app work on
   LAN, remote, and relay without config.
4. Every request needs the `X-Plex-*` header set (client identifier,
   product, version, device) plus `X-Plex-Token`. Use one OkHttp
   interceptor; see Chronicle's `PlexInterceptor.kt`.
5. Books are music albums (`type=9`), chapters/files are tracks (`type=10`).
   Collections: `/library/sections/{id}/collections` and
   `/library/collections/{id}/children`.
6. Downloads = the track part URL + `?download=1` + token header.

## The one invariant

**Position is never lost.** Local Room write happens before anything else on
every pause/interruption/track-change/focus-loss. Plex sync is best-effort
with retry via WorkManager. Conflict resolution: furthest-ahead wins. If a
change risks this invariant, it's wrong.

## Building on this Mac

Headless toolchain, no Android Studio. `local.properties` (gitignored)
points `sdk.dir` at the cmdline-tools SDK — regenerate it with the line
below if missing.

- JDK 17 via `brew install openjdk@17` (formula, not `--cask temurin@17` —
  the cask's installer needs interactive sudo, which isn't available
  headless). `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.
  Not symlinked into `/opt/homebrew/opt/openjdk` (keg-only); export
  `JAVA_HOME` and prepend `$JAVA_HOME/bin` to `PATH` before any Gradle
  invocation.
- Android SDK via `brew install --cask android-commandlinetools`, root at
  `/opt/homebrew/share/android-commandlinetools` (`ANDROID_HOME`/
  `sdk.dir`). Installed: `platform-tools`, `platforms;android-35`,
  `build-tools;35.0.0` (Gradle also auto-installed `build-tools;34.0.0` as
  an AGP dependency). Licenses accepted via `yes | sdkmanager --licenses`.
- Gradle itself (`brew install gradle`) is only used to run `gradle
  wrapper --gradle-version 8.9` once; the project uses `./gradlew`
  (wrapper pinned to 8.9), never the brew-installed `gradle`.
- `./gradlew assembleDebug` is the build gate.

## Accounts / machines

- GitHub: repo lives under **brandonscollins** (personal). On machines where
  the active gh account is edgewoodcommunitychurch:
  `gh auth switch --user brandonscollins` → push → switch back.
- Files/NAS: MP3s on Synology at Brandon's brother's house; Plex server on a
  Windows PC there, pointed at a mapped drive. Remote access via plex.tv.

# Yarn Audiobook Player — PRD

**One-liner:** Smart Audiobook Player's player on Plex's plumbing.

**User:** Brandon. One user, sideloaded APK, never published to the Play
Store. Every scope decision flows from this.

## Problem

Audiobooks (MP3s) live on a Synology NAS; Plex (on a Windows PC pointed at a
mapped drive) already handles metadata, book recognition, login, and remote
access. The best Android players are local-only; the Plex-native clients have
unreliable players. Nobody has built a robust player on top of the server
that already works.

## Division of labor

**Plex does (zero code here):** metadata, book/series organization, auth
(plex.tv), remote access from anywhere (relay, no open ports), progress
storage for cross-device sync.

**Yarn does:** everything between the API and the ear.

## Non-goals

Deliberate exclusions — each would cost a month:

- No metadata editing or library management (Plex owns it)
- No server admin features
- No ebooks, no podcasts
- No Play Store release, no multi-user polish, no iOS
- No transcoding UI (MP3s direct-play)

## Success metric

**Position is never lost.** The single failure that makes a player feel
janky. Every pause, interruption, track change, and audio-focus loss writes
position to local DB first; a WorkManager job syncs to Plex with retry. On
conflict, the furthest-ahead position wins. Local is truth; Plex is backup
and sync.

Secondary: Brandon listens daily and never reopens Chronicle.

## Features

### P0 — daily-usable

| Feature | Notes |
|---|---|
| plex.tv PIN sign-in | `POST /api/v2/pins.json`, poll until approved |
| Server discovery + connection race | `/api/v2/resources`, race all connections local-first, 15s timeout |
| Library browse | Albums-as-books, `type=9` |
| Collections/series browse | Library is already organized this way in Plex |
| Stream with resume | ExoPlayer, direct-play MP3 |
| Progress sync | Timeline API + playQueues session (see gotchas in CLAUDE.md) |
| Playback speed | ExoPlayer playback parameters |
| Sleep timer | Fade ~20–30s, then pause and rewind to fade start |

### P1 — the differentiators

| Feature | Notes |
|---|---|
| **Auto sleep window** | Between user-set hours (e.g. 9:30pm–6am), any playback *start* — including a bluetooth headphone play press — arms the sleep timer at the default duration. Visible "armed — 15:00" chip with tap-to-cancel. |
| Rewind-on-resume | Options: fixed (e.g. 1 min) or smart (scaled by pause length: 10s pause → nothing, overnight → full minute) |
| Downloads, unified player | WorkManager fetch; player just gets a local URI instead of remote. Same screen, same book, both modes. |
| Volume boost | `LoudnessEnhancer` on the player's audio session |
| EQ | Platform `Equalizer` / `DynamicsProcessing` effect |
| Android Auto | Mostly free once MediaSessionService is right |

### P2 — delight

| Feature | Notes |
|---|---|
| Recently played / finished shelf | Plex `lastViewedAt` / view counts |
| Listening stats | Local Room log the player appends to (minutes/day, per book) |
| Search | Local-first over the Room library cache (instant, offline); server search fallback |
| Bookmarks | Local, with optional note |
| Per-book speed memory | Narrators differ |
| Skip silence | ExoPlayer built-in |

## UX surfaces

Four screens plus a persistent mini-player. Dark theme default, dynamic
color, large touch targets over density (one-handed use in bed / in the car).

1. **Home** — "Continue listening" hero card (one tap resumes), recents row.
2. **Library** — cover grid; tabs or filter for Collections/Series; state
   badges (cloud / downloading / downloaded); thin progress bars on covers.
3. **Book detail** — description, chapter/track list with progress, download
   toggle, resume/start-over.
4. **Player** — large cover, scrub bar with chapter ticks, ±30s flanking
   play/pause, one row: speed · sleep · chapters · EQ. All controls in the
   bottom half of the screen.

## Sleep-window spec (the signature feature)

- Settings: window start/end + default duration (e.g. 15 min).
- Arm rule: any transition to *playing* while now ∈ window arms a fresh
  timer. Media-button resumes hit the same MediaSession callback as on-screen
  play, so the sleep-headphones flow is the same code path.
- Fire: fade volume over ~20–30s → pause → seek back to where the fade began
  (half-heard sentences aren't lost).
- Escape hatch: on-player chip showing remaining time; tap to cancel tonight.

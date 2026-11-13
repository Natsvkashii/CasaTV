# CasaTV

CasaTV is a self-hosted, modular IPTV system for your local network. It consists of a high-performance local server, a desktop management app with system tray integration for PC (Windows, macOS, Linux), an Android TV client app, and an optional portable server for Android smartphones.

**Clean by design**: CasaTV does **not** pre-load, bundle, or distribute any IPTV playlists or copyrighted streams. On a fresh installation, the catalog starts completely clean; users configure their own M3U / M3U8 playlists directly from the Desktop interface, the Web Dashboard, or the Android TV client.

The server does the heavy work (catalog management, EPG, logos, HLS stream proxy, profiles, recommendations, history). The TV app is a thin Jetpack Compose client designed for lightning-fast zapping.

The server comes in multiple flavours:

- **Desktop Controller & System Tray** (`CasaTV/desktop`) — Runs on Windows (bottom tray), macOS (top menu bar), and Linux. Manages the server, displays local IP addresses for TV connection, and lets you add/manage M3U playlists with ease.
- **Node.js server** (`CasaTV/server`) — Fastify backend with SQLite, for PC, NUC, Raspberry Pi or NAS.
- **CasaTV Server for Android** (`CasaTV/android-tv/server`) — An Android app that turns any smartphone or tablet into a local CasaTV server with personal library streaming.
- **CasaTV for Android TV** (`CasaTV/android-tv/app`) — Fast, native TV interface with TV-mode zapping, Netflix-style profiles, and playlist management right from the TV remote.

## Features

- 10,000+ channels and 200+ countries from iptv-org, with categories, search,
  logos, quality and an EPG.
- **Netflix-style profiles**: name, one of 20 emoji avatars, app language
  (Italiano/English/Español), preferred country, and independent favorites, hidden
  channels, history and TV-mode country.
- **Regional Home**: a dedicated **TV mode** row comes first, followed by
  recommended, recent, favorites, country and category rows. The country button
  in the Home header switches the TV-mode country and opens its channel queue
  immediately.
- **TV mode built for zapping**: ▲▼ changes channel, 0–9 tunes by number,
  MENU toggles a favorite, OK shows/hides info, INFO (or GUIDE) changes the TV
  country, BACK exits. The zapping queue starts in the selected region and
  stays inside the genre, category, language or country list it was opened
  from. The country picker merges the server facets with the full ISO-3166
  catalog, so it is never empty and every country is available.
- **Language filter**: the Channels tab filters by language and always offers
  the main languages (English, Spanish, Italian, French, German, Portuguese,
  Russian, Arabic, Chinese, Japanese, Korean, Hindi and more), plus any other
  language found in the playlist.
- **Non-blocking channel errors**: an unavailable channel shows a black screen
  with a short message and key hints. There are no dialogs or focusable
  controls, so ▲▼ keeps zapping. OK retries, INFO changes country.
- **Multi-stream failover**: each channel can expose several iptv-org sources.
  The server proxy tries up to six sources; the app also receives the ordered
  source list and moves to the next source automatically when playback fails.
- **m3u-driven updates**: refresh the catalog from the panel, the API or a
  custom m3u URL. Metadata such as `tvg-id`, `tvg-name`, `tvg-logo`,
  `group-title`, `tvg-language` and multi-value `tvg-country` is ingested.
- **Background status service**: a foreground service keeps the app alive when
  it is not on screen and posts a low-priority notification with the connection
  state, last catalog update, connected devices, CPU/RAM/disk usage and a
  warning when resources are almost exhausted.
- **System monitor in Settings**: connection health, last channel update,
  devices currently connected (idle or playing a channel) and live resource
  bars, refreshed automatically every 20 seconds.
- **Italian, English and Spanish** UI and profile language.
- **Personal library on the phone** (Android server): upload MKV/MP4/MOV files
  from the phone, get plot, year, genres, rating and poster from Wikipedia and
  TVMaze, then stream them to the TV with HTTP range requests. The TV shows them
  in a dedicated **Libreria/Library/Biblioteca** tab and plays them in a
  full-screen player with the plot one key press away.
- **M3U playlists on the phone** (Android server): paste a provider link or
  pick an M3U/M3U8 file; the channels appear immediately in the TV's
  **Canales/Canali** tab with country/category filters, search and favorites.
  Playlists can be refreshed or removed from the phone panel.
- HLS proxying without transcoding, logo cache, EPG refresh, stream scanning
  and mDNS discovery.

## Requirements

- Node.js **22.5+** (Node 24 recommended; the server uses the built-in
  `node:sqlite`) for the Node server, **or** the Android server APK below.
- Android TV / Google TV 7.0+ (`minSdk 24`) or an Android TV emulator.
- Android 8.0+ (`minSdk 26`) for the phone acting as server.
- Server and TV on the same LAN. The server binds to `0.0.0.0:8787` by default.

## Server

```bash
cd CasaTV/server
npm install
npm start
```

The first start downloads the iptv-org catalog, which can take a minute or two.
Then open:

- Web panel: `http://localhost:8787`
- From the TV / LAN: `http://<server-ip>:8787`

The server advertises `_casatv._tcp` over mDNS as `CasaTV`; the app can
discover it automatically, or you can type the LAN URL.

### Configuration

Copy `.env.example` to `.env` and edit as needed:

| Variable | Default | Notes |
| --- | --- | --- |
| `PORT` | `8787` | HTTP port |
| `HOST` | `0.0.0.0` | Bind address |
| `PUBLIC_URL` | empty | Public URL used in generated playlists |
| `DATA_DIR` | `./data` | SQLite DB, logo and playlist cache |
| `SERVER_NAME` | `CasaTV` | Name shown in the app and mDNS |
| `DEFAULT_COUNTRY` | `IT` | Default region for new profiles |
| `SOURCE_INDEX_URL` | iptv-org index | Default catalog source |
| `REFRESH_HOURS` | `6` | Catalog refresh interval |
| `DEFAULT_EPG_URL` | epgshare01 IT | Initial EPG source |
| `LATEST_APP_VERSION` | server version | Latest published APK version |
| `APP_DOWNLOAD_URL` | empty | APK link shown when an update exists |
| `APP_CHANGELOG` | empty | Short update note |
| `MIN_APP_VERSION` | empty | Oldest supported app version |

Data lives in `server/data/`: `casatv.db`, cached logos and proxied playlists.

### Web panel

- **Dashboard** — catalog and EPG status, ingest/scan progress.
- **Canali** — search, inspect and preview channels.
- **Guida TV** — EPG programs; add or refresh XMLTV sources.
- **Impostazioni** — server name, default country, auto-scan and EPG refresh,
  NSFW filter, catalog URL and maintenance actions.

### Catalog updates

- Panel button **Aggiorna catalogo**, or `POST /api/refresh`.
- Send an optional m3u URL: `POST /api/refresh` with `{ "url": "https://..." }`.
- The URL can also be saved as the `catalog_url` setting; when empty the
  `SOURCE_INDEX_URL` default is used.
- The scheduler refreshes the catalog every `REFRESH_HOURS` and can refresh the
  EPG and scan stream health automatically.

The catalog is parsed from m3u metadata. Multiple countries in `tvg-country`
are supported, and `tvg-language` is used for localized channel details.

### Status API and connected devices

- `GET /api/system` — uptime, health, alerts, CPU/RAM/disk, last catalog update,
  connected devices and catalog stats. Used by the app's settings screen and
  the background service.
- `POST /api/clients/heartbeat` — registers/refreshes a device. The app also
  sends `X-Device-Id`, `X-Device-Name`, `X-Platform` and `X-App-Version` on
  every request, so a device is visible while it is using the app. Entries
  expire after 90 seconds without traffic (the service sends a heartbeat every
  30 seconds).
- `GET /api/version` — update check for the APK.

### Versions and updates

The server version lives in `server/src/config.js` and `server/package.json`.
The app version is `versionName` (`versionCode` must increase) in
`android-tv/app/build.gradle.kts`.

To publish an update, set `LATEST_APP_VERSION` (and optionally
`APP_DOWNLOAD_URL` / `APP_CHANGELOG`) in `server/.env`. The TV compares its
`BuildConfig.VERSION_NAME` with the server through `/api/system`; when the
server has a newer version, Settings shows **Aggiornamento disponibile** with
the target version. The notification keeps working without an update server.

## Android TV app

Build from `CasaTV/android-tv`:

```bash
JAVA_HOME="/path/to/jdk17" ANDROID_HOME="$HOME/Library/Android/sdk" \
  ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A ready-to-install build is kept in `CasaTV/dist/casatv-tv-<version>.apk`
together with a short release note (`dist/README.txt`).

Open the app, let it discover CasaTV (or enter the server URL), then choose a
profile from the **"Chi sta guardando?"** gate. **Gestisci profili** lets you
create, edit and delete profiles, choose an avatar, language and country.

### Channels tab

The **Canales / Canali / Channels** tab lists the IPTV channels imported in the
Android server (M3U playlist) plus the library items. It supports country,
category and language chips, name search, pagination and favorites. The language
chips always include the main languages even when the playlist only reports a
few; the full country list is also available in every country picker. If the
profile country returns no channels on first open, the app retries once without
the country filter, so custom playlists are visible even when they do not carry
country tags. Selecting a channel starts ExoPlayer on the provider URL directly.

### Library tab

The **Libreria / Library / Biblioteca** tab lists the movies and episodes
uploaded from the phone (poster grid, filter All/Movies/Series, detail panel
with year, runtime, rating, genres and plot). Selecting an item opens
`MoviePlayerScreen`, which streams the file straight from the phone with
ExoPlayer and HTTP range requests:

| Key | In the movie player |
| --- | --- |
| OK | Play/pause (controller) |
| MENU / INFO | Show or hide title, details and plot |
| BACK | Back to the library |

If the phone goes offline or the codec is unsupported, the player shows a
message with **Riprova** and **Torna alla libreria**.

### Profiles and per-profile state

Profiles are stored server-side in SQLite (`profiles` and `profile_channels`).
The app sends `X-Profile-Id` on every request. The first profile is created
automatically and inherits legacy favorites/history from older versions.

- Profile country drives Home recommendations and the starting TV queue.
- The TV country override (set with INFO while watching, or from Home, TV mode
  and Settings) is stored per profile. If the selected country has no channels,
  TV mode shows the whole catalog with a clear fallback notice instead of an
  empty screen.
- Favorites, hidden channels, last-played history and playback state never mix
  between profiles.

### Remote keys

| Key | In the app | In TV mode |
| --- | --- | --- |
| ▲ ▼ | Move focus | Previous / next channel |
| OK | Select | Show/hide info; confirm numeric entry; retry on error |
| 0–9 | – | Tune by channel number |
| MENU | Toggle favorite | Toggle favorite |
| INFO / GUIDE | – | Choose the TV-mode country |
| BACK | Back | Exit playback (or close the country picker) |

### Stream failover

`/api/channels/:id/streams` returns the ordered sources for a channel, each
with a proxy URL (`/stream/:id/playlist.m3u8?source=...`). The proxy rewrites
HLS playlists and tries up to six sources. In TV mode the app prefetches the
list and automatically advances when ExoPlayer reports an error; only after all
sources fail does it show the black error screen.

## CasaTV Server for Android

`android-tv/server` is a separate application (`it.casatv.server`, version
1.1.3, `minSdk 26`, `targetSdk 35`) that runs the whole CasaTV server on a
phone or tablet. It uses NanoHTTPD instead of Node.js and SQLite (plain
`SQLiteOpenHelper`, database `casatv-server.db`) for storage. No transcoding
happens on the phone: the TV receives the original file, and IPTV channels are
played directly from the provider URL.

### What it does

- **Server-managed profiles**: name, emoji avatar, language and country live in
  the phone's database, so the same profile is available on every TV. The TV
  app keeps sending `X-Profile-Id`; responses are localized in the profile
  language, falling back to the server language.
- **Library uploads**: pick movies or episodes from the phone with the system
  file picker. Accepted containers are MKV, MP4, MOV, AVI, WebM, TS and similar.
  Files are copied into the app's private storage; duration and quality are read
  from the file name/metadata.
- **M3U playlists**: import a playlist from an `http(s)` URL or a local
  M3U/M3U8 file with the system picker. Only `http/https` streams are kept;
  `|User-Agent=` options are stripped, `group-title`/`#EXTGRP` become categories
  and `tvg-*` tags fill logo, language, country and quality. Country is also
  inferred from the `tvg-id` suffix, `group-title` and a trailing `(IT)` in the
  name; languages are normalized to ISO 639-2 so the TV filter works with both
  servers. Each import replaces the previous playlist, and
  **Aggiorna/Refresh** re-downloads the stored URL. Channels are then exposed by
  `/api/channels` and the TVs see them together with the library.
- **Online metadata**: Wikipedia (localized title, year, plot, poster) and
  TVMaze (series poster, genres, rating). No API key is needed. Posters are
  downloaded once and served by the phone.
- **Streaming**: `GET /api/library/:id/stream` supports `Range` requests
  (`206 Partial Content`, `Content-Range`, `Accept-Ranges`), so the TV can seek
  inside the movie.
- **Foreground service**: `ServerService` keeps the HTTP server alive while the
  screen is off using a low-priority notification, a partial wake lock and a
  Wi-Fi lock. The foreground service type is `connectedDevice` (no `dataSync`
  6-hour limit on Android 15).
- **Discovery**: the phone advertises `_casatv._tcp.` as `CasaTV Server`, so the
  TV's Setup screen finds it automatically; the address can also be typed.
- **UI in Italian, English and Spanish**: the first launch asks for the
  language; it can be changed later in Settings. The dashboard shows the server
  address, channel and library counters, connected TVs, storage, upload progress
  and recent additions. The **Canali/Canales** tab manages the M3U playlist.
- **Side menu navigation**: a hamburger button opens a drawer with Dashboard,
  Channels, Library, Profiles and Settings. The current section and the server
  address stay visible in the header; no horizontal tab scrolling is needed.

### Build

```bash
cd CasaTV/android-tv
JAVA_HOME="/path/to/jdk17" \
  ./gradlew :server:assembleDebug :app:assembleDebug
adb install -r server/build/outputs/apk/debug/server-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01.
Ready-to-install builds are kept in
`CasaTV/dist/casatv-server-android-1.1.3.apk` and
`CasaTV/dist/casatv-tv-1.3.1.apk`.

### Test on an emulator

```bash
adb forward tcp:9787 tcp:8787        # reach the phone server from the host
curl http://127.0.0.1:9787/healthz
curl http://127.0.0.1:9787/api/library
curl -F "file=@Movie.1999.1080p.mp4" http://127.0.0.1:9787/api/library/upload
```

On the TV use `http://<phone-lan-ip>:8787` (not `127.0.0.1`, which points at the
TV itself).

### Android server endpoints

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/healthz` | `{ok, name, version, channels, streams, epg}` |
| GET/POST/PATCH/DELETE | `/api/profiles` | shared server profiles |
| GET | `/api/config` | server config, active profile, catalog stats |
| GET | `/api/home` | Home rows (IPTV channels by category + library) |
| GET | `/api/channels` | IPTV + library channels; filters `country`, `category`, `language`, `q`, `favorite`, `sort`, `limit`, `offset` |
| GET | `/api/channels/by-number/:n` | channel by number |
| GET | `/api/channels/:id/streams` | ordered stream sources |
| PATCH | `/api/channels/:id` | favorite toggle (`{"favorite":true}`) |
| GET | `/api/playlist` | playlist status (count, source, last import, categories) |
| POST | `/api/playlist` | import from URL (`{"url":"https://…"}`) |
| POST | `/api/playlist/upload` | import a file (multipart, field name `file`) |
| POST | `/api/playlist/refresh` | re-download the stored URL |
| DELETE | `/api/playlist` | remove the playlist and its channels |
| GET | `/api/library` | uploaded movies and episodes |
| POST | `/api/library/upload` | multipart upload, field name `file` |
| GET | `/api/library/:id/stream` | video with `Range` support |
| GET | `/api/library/:id/poster` | poster (JPEG/PNG auto-detected) |
| GET | `/api/system` | CPU/RAM/disk, alerts, connected clients |
| GET | `/api/version` | app update check |

### Limitations

- No transcoding. HEVC/AC3 support depends on the TV/box codecs; MP4 with
  H.264/AAC is the safest choice.
- IPTV channels are streamed directly from the provider: there is no proxy,
  no source failover and no EPG on the Android server. Providers that require a
  specific User-Agent or restrict IPs may refuse playback.
- The TV falls back once to "all countries" when the profile country finds no
  channels, which is what makes custom playlists visible on first open.
- CPU usage is sampled from `/proc/self/stat` because Android denies apps
  access to `/proc/loadavg`; it reflects the server process, not the whole
  device.
- The phone must stay reachable on the same Wi-Fi. mDNS can be blocked by AP
  isolation or some routers; enter the URL manually in that case.
- Large libraries are limited by the phone's free storage (the dashboard shows
  used and free space).

## Using an Android phone as the server (e.g. Galaxy Note 20 Ultra)

The easiest option is the native **CasaTV Server for Android** app above: install
the APK, choose the language and upload your files, no Termux required. The
Node.js server can still be run on the phone with Termux if you want the full
iptv-org catalog and EPG on the device:

Yes, a Note 20 Ultra is more than fast enough: the server does not transcode,
and indexing/proxying uses little CPU. It is a good portable/test server. For a
permanent 24/7 setup, a mini PC, NUC or Raspberry Pi is usually more reliable
because Android aggressively manages background apps and battery/heat.

### Termux setup

1. Install **Termux** from F-Droid or the Termux GitHub releases (the Play Store
   build is obsolete).
2. Update packages and install Node:
   ```bash
   pkg update && pkg upgrade
   pkg install nodejs-lts git
   node -v   # must be 22.5 or newer
   ```
3. Copy the `server` directory to the phone (git clone, `adb push`, or a file
   manager), then:
   ```bash
   cd ~/casatv/server
   npm install --omit=dev
   npm start
   ```
4. Keep Termux alive:
   ```bash
   termux-wake-lock
   ```
   Also disable battery optimization for Termux in Android settings and keep
   the phone on a charger.
5. Open `http://<phone-lan-ip>:8787` from the TV. Reserve a static DHCP lease so
   the IP does not change. mDNS may be blocked on some Android builds; if the
   app cannot discover the server, enter the URL manually.

### Caveats

- Wi-Fi bandwidth matters more than CPU if several TVs stream at once; use
  5 GHz, or a USB-C Ethernet adapter for the phone.
- Avoid direct sun/hot surfaces while charging. Some phones support a charge
  limit (e.g. 85%), which is healthier for a 24/7 server.
- Android may still kill Termux after long idle periods on some OEM builds;
  check the battery settings and use the wake lock.
- The SQLite DB, logo cache and EPG stay in Termux's private storage; back up
  `server/data` if you care about profiles and favorites.
- `node:sqlite` requires Node 22.5+. On older Node 22 releases start with
  `node --experimental-sqlite src/index.js`.

## Troubleshooting

- **A channel shows "Canale non disponibile"** — the upstream iptv-org links
  are down (for example `Cine34` often returns HTTP 502). CasaTV already tried
  every known source. Zap away, retry later, or refresh the catalog.
- **EPG disappears in the afternoon** — the XMLTV source itself ends there.
  Add another EPG source or refresh the guide.
- **The TV cannot find the server** — use the LAN IP, not `localhost`; make
  sure port 8787 is allowed by the firewall and that AP/client isolation is off.
- **Many channels offline** — iptv-org links change frequently. Run **Aggiorna
  catalogo** and then a stream scan from the panel.
- **Slow Home** — use the server-side profiles and keep the TV app as a thin
  client; do not enable transcoding.
- **A movie in the Library tab does not start** — make sure the phone is still
  running CasaTV Server, on the same Wi-Fi and not in deep sleep. Check the
  address from the phone dashboard; if the codec is not supported (often HEVC
  or AC3), use an H.264/AAC MP4 file.
- **The poster or plot is wrong** — the title is guessed from the file name; use
  `Title.Year.Quality.ext` (for example `Matrix.1999.1080p.mp4`) and press
  **Aggiorna info / Refresh info** in the server library.

## Project layout

```
CasaTV/
  desktop/             Cross-platform controller & system tray (Windows, Mac, Linux)
    main.js            Electron main process with Tray integration
    preload.js         Secure contextBridge IPC
    ui/                Dark-themed management UI (index.html, styles.css, renderer.js)
    assets/            Icons and tray templates
  server/              Node.js API, catalog, EPG, proxy, web panel
    src/               db.js, api.js, proxy.js, catalog.js, epg.js, ...
    public/            web panel (vanilla JS)
    data/              SQLite DB and caches (generated)
  android-tv/          Gradle project with two apps
    app/               CasaTV TV client (it.casatv.tv)
      src/main/java/it/casatv/tv/
        data/          API client and DTOs
        ui/            MainViewModel, localization, screens, components
    server/            CasaTV Server for Android (it.casatv.server)
      src/main/java/it/casatv/server/
        core/          prefs, strings, SQLite, library, metadata, filename parser
        server/        NanoHTTPD server, routes, streaming, JSON helpers
        service/       foreground service, mDNS, wake/Wi-Fi locks
        ui/            Compose dashboard, library, profiles, settings
  dist/                ready-to-install APKs and release notes
```

## Disclaimer

CasaTV does not host, provide, or distribute any streams, content, or playlists. It is an open-source tool for managing and streaming user-provided M3U/M3U8 playlists on a local network. Users are solely responsible for the content they configure and play.

## License

Copyright (C) 2026 Guaira Jaramillo Nitoglia <guaira.jaramillo.nitoglia@gmail.com>

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3.0 (GPL-3.0)** as published by the Free Software Foundation. See [LICENSE](LICENSE) for details.

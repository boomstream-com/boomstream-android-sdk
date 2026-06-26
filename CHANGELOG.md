# Changelog

All notable changes to the Boomstream Android SDK are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] — 2026-06-27

### Added

- **`BoomstreamOptions.userAgentToken`** — a single option that injects the restream-bypass / `ua_allow` token into the `User-Agent` of **both** the config request (api-sdk) and the HLS manifest/segment requests (player-sdk). Set it once at `Boomstream.init(...)` instead of wiring the token in two separate places.
- **example-app folder listing** — set `BOOMSTREAM_MEDIA_FOLDER` in `local.properties` to list videos from a specific folder; an empty value lists the account root.

### Changed

- **example-app** — env-var `BOOMSTREAM_DRM_TOKEN` is now the canonical name for the `ua_allow` token; injected into both `BoomstreamOptions.userAgent` (in `ExampleApp.kt`) and `BoomstreamPlayer.allowClearKeyDRMtoken` (in `MainScreen.kt`). Legacy `ALLOWED_UA_KEY_TO_PLAY_AES` is still accepted as a fallback for backwards compat.

### Deprecated

- **`BoomstreamOptions.userAgent`** — use `userAgentToken` instead. `userAgent` keeps working as an explicit override and will be removed in 2.0.

### Documentation

- **README** — consumers must declare **both** `google()` and `mavenCentral()` repositories; `google()` provides the transitive Jetpack Compose / Media3 dependencies.
- **README §«Защищённый контент (ua_allow)» / §«Protected content (ua_allow)»** — wiring the `ua_allow` token for protected content.

---

## [1.0.0] — 2026-06-26

### Added

#### player-sdk

- **Jetpack Compose composable** (`BoomstreamPlayer`) — drop-in composable backed by Media3/ExoPlayer with automatic lifecycle management.
- **Android View component** (`BoomstreamPlayerView`) — XML-inflatable counterpart; both share the same playback engine.
- **AES-encrypted content** — optional per-request AES key header; set via `BoomstreamPlayerConfig.aesKey`.
- **Offline playback** — plays videos previously downloaded via `offline-sdk` with zero network round-trips.
- **Offline-live records** — streams scheduled live broadcasts that finished recording; auto-polls the config endpoint every 10 seconds until the recording becomes available.
- **Stream-offline state for inactive live** — displays a dedicated UI state when a live stream is inactive (no active broadcast source), distinct from network errors.
- **No-network / offline message overlay** — surface-level UX message when the device is offline and no cached content exists.
- **Access-restricted overlay** — shows poster + localised system message when content requires authentication or a paid plan.
- **i18n system messages** (Russian / English) — all player overlays expose a localised string; locale selection mirrors the device locale with a fallback to English.
- **Custom `User-Agent` header** — all API requests from the player carry `Boomstream Android SDK v<version>`.
- **Network-error classification** — `SocketTimeoutException` and similar transient failures are surfaced as `PlayerError.NetworkError` for reliable retry logic.
- **Poster fallback** — falls back to `config.defaults.posters` when the per-media poster list is empty.

#### api-sdk

- **`BoomstreamApi` Kotlin client** — type-safe, coroutine-friendly client over the Boomstream config endpoint.
- **`ConfigResponse` model** — full mapping of the `play.boomstream.com/{code}/config` JSON response, including `mediaData`, `defaults`, `posters`, and live-stream fields.
- **`isLiveOffline` property** — derived field on `ConfigResponse`; `true` when a live stream is inactive (no source); drives the player-sdk stream-offline state.
- **`additionalInterceptors` API** — attach custom OkHttp interceptors (e.g. for `Authorization` headers) without touching internal client configuration.

#### offline-sdk

- **Video download manager** — download Boomstream media for offline playback; tracks progress with `DownloadState` flow.
- **Download lifecycle controls** — pause, resume, and remove individual downloads.
- **Cache integration** — `offline-sdk` cache is automatically recognised by `player-sdk` during playback resolution.

#### CI / build

- **GitLab CI pipeline** — build, unit-test, instrumented-test, Dokka, SCA, and Maven-publish stages.
- **Gradle hard-guard** — rejects accidental non-Release publish attempts at build time.

### Security

- AES key is injected via an HTTP request header and never persisted to disk or written to logs.
- `HttpLoggingInterceptor` is excluded from the published SDK; debug logging must be wired externally via `additionalInterceptors`.
- `BOOMSTREAM_API_KEY` is never embedded in the AAR artifact; injected at runtime via `local.properties` / CI masked variable.

---

<!-- For the next release: add a new ## [x.y.z] — YYYY-MM-DD section at the -->
<!-- top with the changes since the previous version.                       -->

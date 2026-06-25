# Changelog

All notable changes to the Boomstream Android SDK are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v1.0.0] — 2026-06-25

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

- **GitLab CI pipeline** — build, unit-test, instrumented-test, Dokka, OWASP SCA, and Maven-publish stages.
- **Gradle hard-guard** — rejects accidental non-Release publish attempts at build time.
- **OWASP dependency-check (SCA)** — incremental NVD database cache; runs on every pipeline.

### Security

- AES key is injected via an HTTP request header and never persisted to disk or written to logs.
- `HttpLoggingInterceptor` is excluded from the published SDK; debug logging must be wired externally via `additionalInterceptors`.
- `BOOMSTREAM_API_KEY` is never embedded in the AAR artifact; injected at runtime via `local.properties` / CI masked variable.

---

<!-- When v1.0.0 is tagged, rename the [Unreleased] section above to:    -->
<!-- ## [1.0.0] - YYYY-MM-DD                                              -->
<!-- and add a new empty [Unreleased] section at the top.                 -->

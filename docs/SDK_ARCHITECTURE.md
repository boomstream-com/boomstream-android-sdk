# Boomstream Android SDK — архитектура (v1.0)

> Owner: TechLead. Board decision: Hybrid (Apache 2.0, internal Maven, RU README primary).

---

## 0. TL;DR (что зафиксировано)

| Решение | Значение | Раздел |
|---|---|---|
| Модель Gradle | Multi-module: `api-sdk`, `player-sdk`, `offline-sdk`, `example-app` | §1 |
| Публичный API | Kotlin-first (sealed states, `suspend`, `Flow`, `Result`), Java-friendly через `@JvmStatic`/`@JvmOverloads` | §2 |
| UI плеера | Composable первичен (`PlayerSurface` из Media3), View-обёртка как back-compat | §2 |
| Toolchain | AGP 9.2.x, Kotlin 2.1.x (built-in), Gradle 8.10, Compose BOM 2026.05 | §3 |
| `minSdk` | **24** (Android 7.0) — covers 98 %+ active devices | §3 |
| `targetSdk` | **35** (Android 15) — current Play Store requirement | §3 |
| Версионирование | `libs.versions.toml` (version catalog), SemVer | §3 |
| Media-стек | `androidx.media3` 1.10.x (HLS + DRM + offline) | §4 |
| User-Agent | Custom UA через `OkHttpDataSource.Factory` + `DefaultHttpDataSource.Factory`, значение берётся из `BoomstreamOptions.userAgent` (см. §8) | §5 |
| DRM | ClearKey, активируется токеном через User-Agent (`allowClearKeyDRMtoken`) | §5 |
| Offline | Media3 `DownloadService` + `DownloadManager` (НЕ кастомный WorkManager) | §6 |
| API клиент | Retrofit 2.x + kotlinx.serialization + OkHttp 5.x + coroutines | §7 |
| Конфигурация | `Boomstream.init(context, apiKey, options = BoomstreamOptions())`, ключ из `BuildConfig.BOOMSTREAM_API_KEY` (`local.properties` локально, CI env в pipeline) | §8 |
| Hardcoded `const val API_KEY` | **Анти-паттерн** — запрещён в SDK source и в example-app | §8 |
| v1.0 публикация | internal Maven registry, Apache 2.0, AAR signed | §9 |
| CI/CD | GitLab CI: assemble + lint + test + dokka + publish snapshot/release | §10 |

---

## 1. Modularization

### Решение

**Multi-module**, 4 Gradle модуля под одним root project:

```
boomstream-android-sdk/
├── settings.gradle.kts
├── build.gradle.kts                 # root (plugins block + version catalog)
├── gradle/libs.versions.toml
├── api-sdk/                         # Retrofit + DTO + repositories
├── player-sdk/                      # Media3 wrapper + Composable + custom UA
├── offline-sdk/                     # DownloadService + DownloadManager
├── example-app/                     # demonstration app (NOT published)
└── docs/SDK_ARCHITECTURE.md
```

### Зависимости между модулями

```
example-app  ──► player-sdk ──► api-sdk
                   │
                   └────────► offline-sdk ──► api-sdk
```

- `api-sdk` — фундамент. Не зависит ни от Media3, ни от Compose. Может использоваться отдельно (только REST).
- `player-sdk` — UI + воспроизведение. Зависит от `api-sdk` (получить `hls` URL + posters) и от Media3.
- `offline-sdk` — отдельный артефакт; не тянет в process любой player UI. Зависит от `api-sdk` (метаданные) и Media3 download stack.
- `example-app` — `com.android.application`, демонстрация, **не публикуется**.

### `api()` vs `implementation()`

- В `player-sdk/build.gradle.kts`: `api(project(":api-sdk"))` — типы `BoomstreamMedia`, `MediaCode`, `BoomstreamError` всплывают в публичном API плеера, потребитель должен их видеть.
- Media3 — `implementation()`, **не** `api()`. Если потребитель хочет глубоко настраивать `ExoPlayer`, мы предоставляем `BoomstreamPlayer.exoPlayer: ExoPlayer` через explicit accessor (документированный escape-hatch), но не протаскиваем Media3 в transitive API.
- Compose, OkHttp, Retrofit — `implementation()`. Никогда не должны утечь в SDK public API.

### Single-artifact rejected — почему

- Размер: один fat-AAR с Media3 (≈ 4 МБ) + Retrofit + Compose заставит API-only потребителя тащить всё.
- Минификация: потребители без офлайна не должны иметь правил ProGuard для `DownloadService`.
- Эволюция: bump `media3` — внутренняя задача `player-sdk`, не требует major-bump `api-sdk`.

---

## 2. Public API surface

### Идиомы

| Шаблон | Применение |
|---|---|
| `sealed interface` | Состояния плеера (`PlayerState.Idle / Buffering / Playing / Paused / Ended / Error`), результаты API (`Result<T>`/`BoomstreamError`) |
| `suspend fun` | Все API-вызовы (`fetchConfig(mediaCode): BoomstreamMedia`). Cancellation через CoroutineScope потребителя |
| `Flow<T>` | Long-lived подписки: `player.stateFlow: StateFlow<PlayerState>`, `download.progressFlow: Flow<DownloadProgress>` |
| `Result<T>` | Внутренний слой возвращает `Result` для exception-free pipeline. Снаружи — выбрасываем typed `BoomstreamException` (легче interop с RxJava/корутинами потребителя) |
| `@JvmStatic` / `@JvmOverloads` | На всех factory методах и top-level `init` для Java callers |
| `data class` + `Builder` | Конфигурация (`BoomstreamOptions`, `PlayerOptions`) — Kotlin DSL + Java Builder через `@JvmBuilder`-style helper |

### Сигнатуры

```kotlin
// api-sdk
object Boomstream {
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        apiKey: String? = null,
        options: BoomstreamOptions = BoomstreamOptions(),
    ): BoomstreamSdk

    @JvmStatic
    val api: BoomstreamApi
}

interface BoomstreamApi {
    suspend fun fetchConfig(mediaCode: String): BoomstreamMedia
    suspend fun fetchPlaylist(mediaCode: String): List<BoomstreamMedia>
}

sealed interface BoomstreamMedia {
    val mediaCode: String
    val posters: List<Poster>

    data class Authorised(
        override val mediaCode: String,
        val hlsUrl: String,
        val title: String?,
        override val posters: List<Poster>,
    ) : BoomstreamMedia

    data class Unauthorised(
        override val mediaCode: String,
        override val posters: List<Poster>,
    ) : BoomstreamMedia
}

// player-sdk (Compose)
@Composable
fun BoomstreamPlayer(
    mediaCode: String,
    modifier: Modifier = Modifier,
    options: PlayerOptions = PlayerOptions(),
    onState: (PlayerState) -> Unit = {},
)

// player-sdk (View interop — для legacy XML layouts)
class BoomstreamPlayerView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    fun load(mediaCode: String)
    fun release()
    val state: StateFlow<PlayerState>
}
```

### Composable vs View

- **Primary:** Composable `BoomstreamPlayer(...)`. Используем `PlayerSurface` + `ContentFrame` из `androidx.media3:media3-ui-compose` (стабильно с Media3 1.4).
- **Secondary:** `BoomstreamPlayerView` (FrameLayout-wrapper) — для команд, ещё не мигрировавших на Compose. Внутри — то же ядро (`BoomstreamPlayerController`), просто другой UI.
- **Lifecycle:** Compose-версия использует `LocalLifecycleOwner` + `DisposableEffect` для авто-release. View-версия требует ручного `release()` в `onDestroy` (документируем в KDoc + Lint warning через custom Lint rule в `player-sdk`).

### Java interop — критические точки

- Все public top-level / object функции — `@JvmStatic`.
- Конструкторы / билдеры — `@JvmOverloads` для опциональных параметров.
- `Flow<PlayerState>` для Java — экспонируем дополнительно `addListener(PlayerListener)` (Media3-стиль) — иначе Java-разработчики страдают.
- `suspend fun` для Java — экспонируем `Future<BoomstreamMedia>` wrapper через `kotlinx-coroutines-jdk8`'s `future {}`.

### Error model

Один корневой `sealed class BoomstreamError`:

- `Network(cause)` — IO / timeout / DNS
- `Http(statusCode, body)` — non-2xx ответ Boomstream API
- `Unauthorised` — config без `mediaData` (kein доступа)
- `MediaNotFound(mediaCode)` — 404 на config endpoint
- `DrmFailure(cause)` — license fetch / provisioning
- `OfflineUnavailable(reason)` — download missing / expired
- `Unknown(cause)` — sentinel, всегда с `cause`

`BoomstreamException` оборачивает `BoomstreamError` для thrown-flow. Использование в потребительском коде через `runCatching { … }.fold(…)` или Kotlin try/catch.

---

## 3. Toolchain (AGP / Kotlin / Gradle / minSdk / Compose)

### Зафиксированные версии

| Tool | Версия | Обоснование |
|---|---|---|
| Android Gradle Plugin | **9.2.0** (April 2026) | Latest stable, built-in Kotlin support, supports `targetSdk=35` |
| Kotlin | **2.1.x** (через built-in AGP 9.x Kotlin support) | Без separate `org.jetbrains.kotlin.android` plugin — AGP 9.x встроен |
| Gradle | **8.10+** | Минимум для AGP 9.2 |
| `compileSdk` | **35** | Android 15 |
| `targetSdk` | **35** | Текущее требование Google Play |
| `minSdk` | **24** (Android 7.0) | См. ниже |
| Compose BOM | **2026.05.01** | Material3 1.4, lifecycle-runtime-compose 2.9 |
| `media3` | **1.10.x** | Latest stable (март 2026), HLS interstitial fixes |
| JVM target | **17** | AGP 9 рекомендует, нет смысла держать 11/8 в новом SDK |
| Java desugaring | enabled (`isCoreLibraryDesugaringEnabled = true`) | Для `java.time.*` API на API 24-25 |

### `minSdk = 24` — обоснование

| Опция | Покрытие | Минусы |
|---|---|---|
| 21 (Lollipop) | ~99.9 % | Накладные расходы на multidex/legacy APIs; Media3 уже не поддерживает Compose+ниже 21; нет smart-cast улучшений Kotlin 2.x |
| **24 (Nougat)** | **~98 %+** | Чистый Java 8/desugar путь, нативный `java.time`, нативный `Locale.forLanguageTag`, `java.util.Optional`, no-multidex |
| 26 (Oreo) | ~93 % | Теряем 5 % устройств без существенного выигрыша |
| 28 (Pie) | ~88 % | Только если бы требовали современный SSL / CryptoExtensions |

`24` — sweet spot: достаточно современный для clean APIs, покрывает почти все живые устройства, унаследованный customer-app (boomstream-customer-app) — `minSdk` 24, наш SDK должен быть совместим.

### `targetSdk = 35`

Google Play требует `targetSdk >= 34` для новых релизов с авг 2025; SDK должен быть на одном шаге впереди приложения-потребителя, поэтому 35.

### Version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "9.2.0"
kotlin = "2.1.21"
media3 = "1.10.0"
retrofit = "2.11.0"
okhttp = "5.0.0"
serialization = "1.7.3"
coroutines = "1.10.1"
composeBom = "2026.05.01"
lifecycle = "2.9.0"

[libraries]
media3-exoplayer       = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls   = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-exoplayer-dash  = { module = "androidx.media3:media3-exoplayer-dash", version.ref = "media3" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
media3-ui-compose      = { module = "androidx.media3:media3-ui-compose", version.ref = "media3" }
media3-session         = { module = "androidx.media3:media3-session", version.ref = "media3" }
media3-exoplayer-drm   = { module = "androidx.media3:media3-exoplayer-drm", version.ref = "media3" }
retrofit-core          = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-converter = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version = "1.0.0" }
okhttp-core            = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging         = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
compose-bom            = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui             = { module = "androidx.compose.ui:ui" }
compose-material3      = { module = "androidx.compose.material3:material3" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }

[plugins]
android-library        = { id = "com.android.library", version.ref = "agp" }
android-application    = { id = "com.android.application", version.ref = "agp" }
kotlin-serialization   = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
dokka                  = { id = "org.jetbrains.dokka", version = "2.0.0" }
maven-publish          = { id = "maven-publish" }
```

### Compose BOM vs legacy View

- Compose **primary**, потому что (а) Media3 уже даёт первоклассные Compose-обёртки (`PlayerSurface`, `ContentFrame`); (б) Boomstream-customer-app — Compose-stack; (в) новые потребители почти всегда Compose. Compose не значит «отказ от View» — `BoomstreamPlayerView` остаётся для legacy.

---

## 4. Media library

### Решение

**`androidx.media3` 1.10.x.** Стандарт де-факто Google с конца 2024, легаси `com.google.android.exoplayer:exoplayer` **deprecated Q4 2024** и больше не получает обновлений.

### Зависимости (player-sdk)

```kotlin
dependencies {
    implementation(libs.media3.exoplayer)        // core
    implementation(libs.media3.exoplayer.hls)    // HLS playback
    implementation(libs.media3.exoplayer.drm)    // ClearKey DRM
    implementation(libs.media3.datasource.okhttp) // custom UA via OkHttp factory (см. §5)
    implementation(libs.media3.ui.compose)       // PlayerSurface, ContentFrame
    implementation(libs.media3.session)          // background playback, MediaSession
    // DASH намеренно НЕ включаем — Boomstream HLS-only по контракту (см. §5)
}
```

### Почему НЕ legacy ExoPlayer

- Deprecated. Без security-fixes. Любая CVE в 2.20.x останется unpatched.
- Compose-обёртки доступны только в Media3.
- Media3 1.10 уже обогнал ExoPlayer 2.x по фичам (HLS interstitial, DRM, background playback APIs).

### Playback flow

1. Получили `BoomstreamMedia.Authorised(hlsUrl, ...)` через `api-sdk`.
2. Создали `MediaItem.Builder().setUri(hlsUrl).setMediaMetadata(...)` (если playlist — список `MediaItem`).
3. Передали в `ExoPlayer.Builder(context).setMediaSourceFactory(boomstreamMediaSourceFactory).build()`.
4. `boomstreamMediaSourceFactory` — `DefaultMediaSourceFactory` с custom `DataSource.Factory` (для UA, см. §5).

### Background playback / MediaSession

- Включаем `androidx.media3:media3-session`. Создаём `MediaSessionService` подкласс в `player-sdk` для интеграции с media buttons / notification / Android Auto.
- Опционально: потребитель отключает через `PlayerOptions(backgroundPlayback = false)` — мы тогда не регистрируем сервис.

---

## 5. HLS + custom User-Agent + ClearKey DRM

### Implementation — custom User-Agent injection

```kotlin
// player-sdk: BoomstreamHttpDataSourceFactory
internal class BoomstreamHttpDataSourceFactory(
    private val okHttpClient: OkHttpClient,        // shared OkHttp, no logging в release
    private val userAgentProvider: () -> String,    // lazy: достаёт актуальный userAgent из BoomstreamOptions
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val factory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgentProvider())
        return factory.createDataSource()
    }
}
```

- **Почему `OkHttpDataSource.Factory`, не `DefaultHttpDataSource.Factory`:** OkHttp factory переиспользует shared connection pool и retry logic. `Default…` дублирует низкоуровневый HTTP стек.
- **Почему `() -> String`, не `String`:** значение может обновляться runtime'но (server config refresh). Lambda гарантирует, что каждое создание `DataSource` берёт актуальное значение из `BoomstreamOptions.userAgent` (см. §8).
- **Per-MediaItem UA override:** если потребитель хочет другой UA на конкретный stream — `BoomstreamPlayer.load(mediaCode, userAgentOverride = "…")`. Override применяется только к данному `MediaItem`.

### ClearKey DRM

Boomstream использует ClearKey DRM, активируемый токеном через User-Agent. SDK передаёт `allowClearKeyDRMtoken` как часть строки User-Agent (`BoomstreamHttpDataSourceFactory`, см. выше) — медиа-сервер проверяет токен и активирует ClearKey-лицензию для данного потока.

Если HLS-манифест содержит `#EXT-X-KEY:METHOD=SAMPLE-AES` или `AES-128` с ClearKey-URI, ExoPlayer инициирует DRM-сессию автоматически. Для SDK потребитель не выполняет дополнительных шагов — DRM прозрачен при корректном UA-токене.

### TLS

HTTPS enforced through hardcoded `https://` base URLs (`play.boomstream.com`, `api.boomstream.com`). На Android 7+ platform-default trust store применяется автоматически — пользовательские CA-сертификаты не доверяются без явной `network_security_config.xml`. Certificate pinning не реализован в v1.0 (запланирован на v1.1+).

### Что **не** делаем

- HLS interstitial ads — Media3 1.10 поддерживает; не включаем в v1.0.
- DASH — Boomstream HLS-only по контракту.

---

## 6. Offline downloads

### Решение

**Media3 `DownloadService` + `DownloadManager`** (а не кастомный WorkManager-based pipeline).

### Обоснование

- `DownloadService` ↔ `DownloadManager` уже умеет:
  - Параллельные / serialized download жобы.
  - Pause / resume / cancel (включая graceful service shutdown).
  - Network constraints (Wi-Fi only, metered allowed).
  - Storage abstraction через `Cache` (`SimpleCache` + `LeastRecentlyUsedCacheEvictor`).
  - HLS-specific: загрузка всех `.ts` сегментов одного качества, выбор bitrate через `DownloadHelper.forMediaItem(...).getTracks()`.
- Кастомный WorkManager pipeline переизобретает всё это.
- Background restrictions Android 12+ (`FGS_TYPE_DATA_SYNC`) уже учтены в Media3.

### Архитектура `offline-sdk`

```kotlin
// Public API
interface BoomstreamDownloads {
    suspend fun start(mediaCode: String, qualityHint: QualityHint = QualityHint.AUTO): DownloadHandle
    suspend fun pause(mediaCode: String)
    suspend fun resume(mediaCode: String)
    suspend fun cancel(mediaCode: String)
    fun observe(mediaCode: String): Flow<DownloadState>
    fun observeAll(): Flow<List<DownloadState>>
    suspend fun delete(mediaCode: String)
    suspend fun stats(): StorageStats
}

sealed interface DownloadState {
    data class Queued(val mediaCode: String) : DownloadState
    data class InProgress(val mediaCode: String, val percent: Float, val bytesDownloaded: Long) : DownloadState
    data class Paused(val mediaCode: String, val percent: Float) : DownloadState
    data class Completed(val mediaCode: String, val sizeBytes: Long, val expiresAt: Instant?) : DownloadState
    data class Failed(val mediaCode: String, val error: BoomstreamError) : DownloadState
}
```

Внутри:

- `BoomstreamDownloadService : DownloadService(notificationChannelId, ...)` — наш `DownloadService` subclass с notification (text/icon конфигурируется при регистрации сервиса потребителем).
- `DownloadManager(context, databaseProvider, cache, factoryWithUA)` — синглтон через `Boomstream.init()`.
- `DownloadHelper.forMediaItem(...)` для построения `DownloadRequest`.

### Storage

- **Internal по умолчанию** — `context.filesDir / "boomstream-downloads"`. Не доступно через MediaStore, не подлежит scoped-storage чистке.
- **External — opt-in** через `context.getExternalFilesDir(...)`. Удаляется при uninstall.
- **External public (MediaStore)** — **не поддерживаем v1.0**.

### Cleanup / TTL

- Завершённые downloads имеют `expiresAt: Instant?` (приходит из server config — TTL лицензии).
- `BoomstreamDownloads.purgeExpired()` — convenience API, дёргается потребителем или через WorkManager periodic worker (потребитель регистрирует сам, мы документируем рецепт).

### Network constraints

- По умолчанию: `Requirements.NETWORK_UNMETERED` (Wi-Fi only).
- Override: передать `Requirements.NETWORK` при создании `DownloadManager` (любая сеть).

### LRU eviction

- `SimpleCache` с `LeastRecentlyUsedCacheEvictor(maxBytes)` — лимит задаётся при инициализации `DownloadManager`.
- Эвикции не трогают completed/locked downloads (LRU работает только на cache-tier, для downloads используем persistent storage).

---

## 7. API client

### Решение

**Retrofit 2.11 + kotlinx.serialization + OkHttp 5.0 + coroutines.**

### Почему Retrofit над Ktor

| Критерий | Retrofit | Ktor Client |
|---|---|---|
| Размер | ~120 КБ (без OkHttp) | ~250 КБ (с CIO) |
| Maturity | 11 лет, де-факто стандарт Android | Активно развивается, но молодой |
| Tooling | Generated API interfaces, lint-friendly | Manual DSL |
| OkHttp интеграция | Native (общий клиент с Media3) | Через `OkHttp engine` (overhead) |
| Coroutines | First-class через `suspend` | First-class |
| Multiplatform | Android-only | KMP-ready |

Решающий фактор: **Media3 уже использует OkHttp DataSource (§5)**. Если мы возьмём Ktor — будем держать 2 HTTP-стека в одном SDK. Retrofit + OkHttp = единый стек.

KMP не требуется (board decision — Android-only v1.0; iOS — отдельный SDK в другом репо).

### Архитектура

```kotlin
// api-sdk
internal interface BoomstreamRetrofitApi {
    @GET("{mediaCode}/config")
    suspend fun config(@Path("mediaCode") mediaCode: String): ConfigResponseDto
}

@Serializable
internal data class ConfigResponseDto(
    val mediaData: List<MediaDto>? = null,    // null = unauthorised
    val posters: List<PosterDto> = emptyList(),
    val links: LinksDto? = null,
)

@Serializable
internal data class MediaDto(
    val mediaCode: String,
    val title: String? = null,
    val links: LinksDto,
)

@Serializable
internal data class LinksDto(
    val hls: String,
)
```

### OkHttp config

- Один `OkHttpClient` синглтон, расшаренный между Retrofit, Media3 `OkHttpDataSource`, и `offline-sdk`.
- Interceptors:
  - **AuthInterceptor** — добавляет `Authorization: Bearer <api-key>` (если `apiKey` передан в `Boomstream.init()`).
  - **RetryInterceptor** — на 5xx + network errors, exponential backoff (3 attempts max).
  - **TimeoutInterceptor** — connect / read timeouts берутся из `BoomstreamOptions.connectTimeoutSeconds` / `readTimeoutSeconds` (defaults: 15s / 30s).
  - **LoggingInterceptor** — `Level.NONE` в release, `Level.BASIC` в debug. **Никогда `Level.HEADERS`/`Level.BODY` в release** (см. §5).

### Caching

- HTTP cache OkHttp (`Cache(cacheDir, 5 МБ)`) — для GET-config (краткосрочный).
- TTL: следуем `Cache-Control` headers сервера. Если сервер их не шлёт — дефолт `max-age=60`.

### Cancellation

- Все `suspend fun` отмена через корутины потребителя. Никаких ручных `Call.cancel()`.

---

## 8. Configuration & DI

### Решение

```kotlin
// Full SDK (player + offline + API):
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // API key comes from BuildConfig, injected from local.properties or CI env.
        // Never hardcode the key value in source code.
        Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY)
    }
}

// Optional: customise timeouts or User-Agent:
Boomstream.init(
    context = this,
    apiKey = BuildConfig.BOOMSTREAM_API_KEY,
    options = BoomstreamOptions(
        connectTimeoutSeconds = 10L,
        readTimeoutSeconds = 30L,
    ),
)

// Player/offline-only (no API key needed):
Boomstream.init(this)  // apiKey defaults to null
```

`BuildConfig.BOOMSTREAM_API_KEY` инжектится через `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        buildConfigField(
            "String",
            "BOOMSTREAM_API_KEY",
            "\"${gradleLocalProperties(rootDir, providers).getProperty("BOOMSTREAM_API_KEY") ?: System.getenv("BOOMSTREAM_API_KEY") ?: ""}\"",
        )
    }
    buildFeatures { buildConfig = true }
}
```

- **Локально:** ключ читается из `local.properties` (gitignored).
- **CI:** ключ читается из CI secret `BOOMSTREAM_API_KEY`.
- **Никогда** ключ в git-tracked source, никогда в repo README, никогда в example-app source code.

### Ключевые гарантии

- `BOOMSTREAM_API_KEY` принимается SDK как параметр в `Boomstream.init(apiKey, ...)`. SDK не embed'ит ключ в bytecode.
- `BoomstreamOptions` — `class`, **не `data class`** → custom `toString()` маскирует `userAgent` как `"***"` (т.к. `userAgent` может содержать media-server-key / `allowClearKeyDRMtoken`).
- `apiKey` не сохраняется в SDK-объекте после `init()` — передаётся напрямую в `BoomstreamSdk` и используется только при конфигурировании OkHttp interceptor.
- `Boomstream.init()` идемпотентен — повторный вызов с другим config'ом **бросает** `IllegalStateException`; reinit запрещён.
- OkHttp `LoggingInterceptor` — `Level.NONE` в release builds (enforced через build-type config + Lint check).
- `const val API_KEY` в SDK source **запрещён** — Lint rule + CI grep.

### DI

- **Никаких внешних DI-фреймворков** (Hilt / Koin / Dagger) — потребитель не должен тащить нашу DI.
- Внутри SDK — manual constructor injection, single `BoomstreamRegistry` который держит синглтоны (OkHttp client, Retrofit instance, DownloadManager).
- `Boomstream.init()` создаёт `BoomstreamRegistry` и хранит в `internal val`. Все public API (`Boomstream.api`, `BoomstreamPlayer`, `Boomstream.downloads`) читают из registry.

---

## 9. Packaging v1.0 — Maven

### Решение

- **Repository:** internal Maven registry (URL provisioned via CI configuration)
- **GroupId:** `com.boomstream.android.sdk`
- **ArtifactIds:** `api-sdk`, `player-sdk`, `offline-sdk`
- **License:** Apache 2.0 (board decision: Hybrid)
- **Signing:** AAR signed через `signing` plugin + PGP-ключ команды (хранится в GitLab CI secret `BOOMSTREAM_SIGNING_KEY` + `BOOMSTREAM_SIGNING_PASSWORD`)
- **POM metadata:** name, description, url, licenses, developers, scm — заполнены полностью
- **Dokka:** генерируется `dokkaHtml` task, публикуется как separate `-javadoc` artifact

### Build types

| Type | Когда |
|---|---|
| `debug` | Локальный develop. Logging interceptor `Level.BASIC`. |
| `release` | Publish snapshot/release. Logging `Level.NONE`, R8 minification on, debuggable=false. |
| `staging` (optional) | Pre-publish smoke с release-like config против staging-серверов |

### ProGuard / R8

- `consumer-rules.pro` в каждом модуле — правила, наследуемые потребителем (keep public API, keep `data class`-fields для serialization).
- `proguard-rules.pro` — internal обфускация.
- kotlinx.serialization keep rules — обязательно.

### LICENSE

```
boomstream-android-sdk/LICENSE       <-- Apache 2.0 полный текст, корневой
boomstream-android-sdk/NOTICE        <-- attribution для Media3, OkHttp, Retrofit
boomstream-android-sdk/COPYRIGHT     <-- "© 2026 Boomstream / HWDmedia"
```

POM `<licenses>` секция указывает на LICENSE-URL в SDK репозитории.

### Publish workflow

1. CI на feature-branch: `./gradlew assemble check dokkaHtml` → артефакты в `build/outputs/aar/`.
2. CI на `develop`: + `./gradlew publishAllPublicationsToMavenSnapshotRepository` → snapshot `1.0.0-SNAPSHOT`.
3. CI на tag `v1.0.0`: + `./gradlew publishAllPublicationsToMavenReleaseRepository` → release `1.0.0`.
4. Tag triggers — manual через Release Engineer, не automatic.

---

## 10. CI/CD (GitLab CI)

### Pipeline stages

```yaml
stages:
  - assemble
  - test
  - lint
  - sca
  - docs
  - publish

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2"

.android: &android
  image: ghcr.io/cimg/android:2026.05-node
  cache:
    key: gradle-${CI_COMMIT_REF_SLUG}
    paths:
      - .gradle/
      - ~/.gradle/caches/

assemble:
  <<: *android
  stage: assemble
  script:
    - ./gradlew assembleRelease --no-daemon

unit-test:
  <<: *android
  stage: test
  script:
    - ./gradlew testReleaseUnitTest --no-daemon

instrumentation-test:
  <<: *android
  stage: test
  only: [develop, /^release\/.*/]
  script:
    - ./gradlew connectedReleaseAndroidTest --no-daemon
  # Требует эмулятора в CI runner — настройка отдельным issue

lint:
  <<: *android
  stage: lint
  script:
    - ./gradlew lintRelease detekt --no-daemon
  artifacts:
    when: always
    reports:
      junit: '**/build/reports/lint-results-release.xml'

sca:
  <<: *android
  stage: sca
  script:
    - ./gradlew dependencyCheckAggregate --no-daemon
  artifacts:
    when: always
    paths:
      - build/reports/dependency-check-report.html
    reports:
      junit: build/reports/dependency-check-junit.xml
  allow_failure: false

dokka:
  <<: *android
  stage: docs
  only: [develop, /^release\/.*/]
  script:
    - ./gradlew dokkaHtml --no-daemon
  artifacts:
    paths: [build/dokka/]

publish-snapshot:
  <<: *android
  stage: publish
  only: [develop]
  script:
    - ./gradlew publishAllPublicationsToMavenSnapshotRepository
  environment: { name: snapshot }

publish-release:
  <<: *android
  stage: publish
  only: { refs: [/^v\d+\.\d+\.\d+$/] }
  when: manual
  script:
    - ./gradlew publishAllPublicationsToMavenReleaseRepository
  environment: { name: production }
```

### Static analysis tools

| Tool | Зачем |
|---|---|
| **Android Lint** | API misuse, deprecated calls, ProGuard rules sanity |
| **detekt** | Kotlin code style + complexity + threading rules |
| **ktlint** | Code formatting (через detekt-ktlint-rules) |
| **dependency-analysis-gradle-plugin** | Detect unused / misused dependencies |
| **OWASP dependency-check** | SCA gate — CVE scan транзитивных зависимостей. CVSS ≥ 7.0 блокирует CI; CVSS 4.0-6.9 — warning в JUNIT-отчёте. Suppressions: `dependency-check-suppressions.xml`. |

### Cache + parallelism

- Gradle daemon **off** в CI (CI runner ephemeral — daemon бесполезен, мешает кэшу).
- Gradle build cache local в `~/.gradle/caches/`, shared между jobs через CI cache.
- `org.gradle.parallel=true` — модули `api-sdk`/`player-sdk`/`offline-sdk` собираются параллельно.

### Required CI secrets

- `BOOMSTREAM_API_KEY` — для example-app instrumentation tests.
- `BOOMSTREAM_SIGNING_KEY` (PGP private key, base64) — для signed publish.
- `BOOMSTREAM_SIGNING_PASSWORD` — passphrase для PGP key.
- `PUBLISH_TOKEN` — token с правами на публикацию артефактов в Maven registry.

---

## Sources

- [Media3 1.10 release notes (March 2026)](https://android-developers.googleblog.com/2026/03/media3-110-is-out.html)
- [AGP 9.2.0 release notes (April 2026)](https://developer.android.com/build/releases/gradle-plugin)
- [AGP 9.0 / built-in Kotlin support (January 2026)](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- [Media3 migration guide](https://developer.android.com/media/media3/exoplayer/migration-guide)
- [androidx/media GitHub](https://github.com/androidx/media)

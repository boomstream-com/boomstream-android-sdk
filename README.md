# Boomstream Android SDK

[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-24-orange)](#)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0%2B-purple)](#)

Нативный Android SDK для встраивания Boomstream-плеера, offline-загрузок и работы с Boomstream API. Под капотом — [Media3 / ExoPlayer](https://developer.android.com/media/media3) + Jetpack Compose. Поставляется как набор отдельных модулей: используйте только то, что нужно вашему приложению.

Код SDK — на [GitHub](https://github.com/boomstream-com/boomstream-android-sdk). Артефакты публикуются в Maven Central.

---

## Quick start (5 минут до первого видео)

### 1. Убедитесь, что Maven Central подключён

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Нужны **оба** репозитория: `mavenCentral()` отдаёт модули `com.boomstream:*`, а `google()` — транзитивные зависимости (Jetpack Compose, Media3). Оба уже есть в стандартном Android-проекте; дополнительных репозиториев и токенов не нужно.

### 2. Добавьте зависимости

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.boomstream:player-sdk:1.7.0")
    implementation("com.boomstream:api-sdk:1.7.0")
    implementation("com.boomstream:offline-sdk:1.7.0") // опционально — только если нужны offline-загрузки
}
```

Версии — в [CHANGELOG.md](CHANGELOG.md). Все модули релизятся синхронно.

### 3. Инициализируйте SDK

`MyApplication.kt`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY)
    }
}
```

API-ключ нужен только для функций `api-sdk` (списки видео, трансляций, плейлистов). Если вы используете только плеер (`player-sdk`) или offline-загрузки — API-вызовы не выполняются. API-ключ — **не** хардкодом. См. [Configuration & secrets](#configuration--secrets).

### 4. Покажите плеер

```kotlin
@Composable
fun MyScreen() {
    BoomstreamPlayer(
        mediaCode = "Il4lNOfL",
        configClient = Boomstream.configClient,
    )
}
```

Composable сам подтянет конфиг (`https://play.boomstream.com/Il4lNOfL/config`), HLS-манифест, постер и сыграет видео. Если в ответе пришёл массив `mediaData` — это плейлист, и плеер автоматически переключает дорожки. Если доступа нет (нет `mediaData`) — покажет постер из `posters`.

Для классической View-системы вместо Compose:

```kotlin
val view = findViewById<BoomstreamPlayerView>(R.id.player)
view.load(mediaCode = "Il4lNOfL", configClient = Boomstream.configClient)
```

---

## Структура репозитория

| Модуль | Maven artifact | Назначение |
|---|---|---|
| [`:api-sdk`](api-sdk/) | `com.boomstream:api-sdk` | Type-safe Kotlin клиент Boomstream API (см. [api.boomstream.com](https://api.boomstream.com/)) + config endpoint (`play.boomstream.com/{mediaCode}/config`) |
| [`:player-sdk`](player-sdk/) | `com.boomstream:player-sdk` | Media3-плеер (`BoomstreamPlayer` Composable + `BoomstreamPlayerView`), playlist, poster fallback |
| [`:offline-sdk`](offline-sdk/) | `com.boomstream:offline-sdk` | HLS offline downloads через Media3 `DownloadService` + `DownloadManager` |
| [`:example-app`](example-app/) | (не публикуется) | Демо-APK: вертикальная разметка — плеер сверху + описание/кнопка «Скачать в офлайн», горизонтальная — fullscreen |

Зависимости между модулями:

```
player-sdk ──► api-sdk
offline-sdk ──► api-sdk
example-app ──► player-sdk, offline-sdk, api-sdk
```

---

## Модуль `player-sdk`

### Подключение

```kotlin
implementation("com.boomstream:player-sdk:1.7.0")
```

### Jetpack Compose

```kotlin
@Composable
fun VideoScreen(mediaCode: String) {
    BoomstreamPlayer(
        mediaCode = mediaCode,
        configClient = Boomstream.configClient,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}
```

### View-система

```xml
<!-- layout/activity_main.xml -->
<com.boomstream.sdk.player.BoomstreamPlayerView
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
val player = findViewById<BoomstreamPlayerView>(R.id.player)
player.load(mediaCode = "Il4lNOfL", configClient = Boomstream.configClient)
```

### Тип поверхности видео (SurfaceView / TextureView)

По умолчанию плеер рендерит в `SurfaceView`. Если на отдельных устройствах (замечено на Android 16)
приложение нативно падает при повороте экрана (`PROCESS ENDED` без Java-стектрейса), переключитесь на
`TextureView` — он переживает поворот без пересоздания surface:

```xml
<com.boomstream.sdk.player.BoomstreamPlayerView
    xmlns:app="http://schemas.android.com/apk/res-auto"
    app:boomstreamSurfaceType="texture_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

В Compose — параметр `surfaceType = BoomstreamSurfaceType.TEXTURE_VIEW`. Подробности и компромиссы:
[docs/PLAYER-API.md → Surface type](docs/PLAYER-API.md#surface-type-surfaceview-vs-textureview).

### Контент с защитой Clear Key DRM / `ua_allow`

Если контент защищён нативным Clear Key DRM, медиасервер проверяет специальный токен в `User-Agent` как сегментных запросов, **так и** конфиг-запроса. Задайте токен один раз при инициализации SDK:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Boomstream.init(
            this,
            BuildConfig.BOOMSTREAM_API_KEY,
            BoomstreamOptions(
                userAgentToken = BuildConfig.ALLOWED_UA_KEY_TO_PLAY_AES.ifBlank { null },
            ),
        )
    }
}
```

После этого `BoomstreamPlayer` и `BoomstreamPlayerView.load()` не требуют дополнительных параметров:

```kotlin
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
)
```

Значение `ALLOWED_UA_KEY_TO_PLAY_AES` задаётся в настройках проекта на [boomstream.com](https://boomstream.com): вкладка **«Защита»** → поле **«Разрешить User-Agent, игнорируя защиту от рестримминга»**.

Токен приходит из `local.properties` или CI-секрета — **никогда** не хардкодьте его в исходниках. **Не логируйте `userAgentToken`.**

#### Миграция с `allowClearKeyDRMtoken` / `options.userAgent`

Если у вас уже работает через старый workaround — он продолжит работать без правок кода:

```kotlin
// Старый способ — по-прежнему работает, можно мигрировать в любой момент
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    allowClearKeyDRMtoken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
)
```

`options.userAgent` в `BoomstreamOptions` помечен `@Deprecated` — перенесите токен в `userAgentToken` при ближайшем удобном случае. Параметр будет удалён в 2.0.

### Player events & controls

Starting with v1.2.0, the SDK exposes a public `BoomstreamPlayerController` for programmatic playback control and event observation.

**Compose** — obtain a controller, pass it to the composable, and collect:

```kotlin
val controller = rememberBoomstreamPlayerController()
BoomstreamPlayer(mediaCode = "Il4lNOfL", configClient = Boomstream.configClient, controller = controller)

// Progress bar
val progress by controller.progressFlow.collectAsState()
LinearProgressIndicator(progress = { progress.percent })

// 70% completion trigger
LaunchedEffect(controller) {
    controller.progressFlow.collect { p ->
        if (p.percent >= 0.7f) fireCompletionEvent()
    }
}
```

**View** — access the stable `controller` property:

```kotlin
val ctrl = playerView.controller
ctrl.play()
ctrl.seekToPercent(0.5f) // jump to 50%
ctrl.setVolume(80)
```

For the full event table, all control methods, fullscreen recipe, and web-to-native mapping, see [docs/PLAYER-API.md](docs/PLAYER-API.md).

### Video quality selection

Начиная с v1.5.0, `BoomstreamPlayerController` даёт доступ к вариантам качества из HLS-манифеста —
можно переключать программно или показать встроенную кнопку выбора в контролах плеера.

```kotlin
val controller = rememberBoomstreamPlayerController()
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    controller = controller,
    // Опционально: строка «Качество» в панели настроек ⚙ (по умолчанию off)
    advancedOptions = AdvancedPlayerOptions(enableQualitySelector = true),
)

// Программное переключение
val options by controller.availableQualities.collectAsState() // пусто до готовности треков
options.filterIsInstance<VideoQuality.Resolution>().forEach { q ->
    Button(onClick = { controller.selectQuality(q) }) { Text(q.label) }
}
Button(onClick = { controller.selectAuto() }) { Text("Auto") }

// Событие смены качества
LaunchedEffect(controller) {
    controller.events.collect { if (it is PlayerEvent.QualityChanged) analytics.log(it.quality) }
}
```

Варианты качества берутся из HLS master-манифеста и появляются после первого track-ready события
(до этого `availableQualities` — пустой список). Подробности —
[docs/PLAYER-API.md → Video quality selection](docs/PLAYER-API.md#video-quality-selection).

### Styling / theming

Начиная с v1.6.0, визуальные параметры плеера выносятся через `BoomstreamPlayerStyle`.

```kotlin
// Compose — передаётся через параметр style
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    style = BoomstreamPlayerStyle(
        loaderColor    = Color(0xFF662BFF).toArgb(), // цвет спиннера загрузки
        accentColor    = Color(0xFF662BFF).toArgb(), // иконки кнопок (best-effort)
        seekBarPlayedColor   = Color(0xFF662BFF).toArgb(),
        seekBarScrubberColor = Color(0xFF662BFF).toArgb(),
    ),
)
```

```xml
<!-- View — XML-атрибуты на BoomstreamPlayerView -->
<com.boomstream.sdk.player.BoomstreamPlayerView
    xmlns:app="http://schemas.android.com/apk/res-auto"
    app:boomstreamLoaderColor="@color/brand_violet"
    app:boomstreamAccentColor="@color/brand_violet"
    app:boomstreamSeekBarPlayedColor="@color/brand_violet"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
// View — программная установка (применяется вживую, без перезагрузки)
player.setLoaderColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setAccentColor(ContextCompat.getColor(this, R.color.brand_violet))
```

Полный список атрибутов и поведение — [docs/PLAYER-API.md → Styling / theming](docs/PLAYER-API.md#styling--theming).

### Offline playback

После загрузки через `offline-sdk` передайте `offlineCache` для воспроизведения из кеша:

```kotlin
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
)
```

Плеер автоматически отдаёт загруженные сегменты из локального хранилища, fallback на сеть — для ещё не скачанных.

### Google Cast (v1.7.0+)

> **v1 — только незащищённый контент.** Проекты с включённой защитой от скачивания не поддерживают Cast в v1. DRM-защищённый Cast запланирован на следующий этап.

**1. Объявите провайдер в манифесте:**

```xml
<!-- AndroidManifest.xml -->
<application …>
    <meta-data
        android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
        android:value="com.boomstream.sdk.player.BoomstreamCastOptionsProvider" />
</application>
```

**2. Требуется AppCompat-тема** (для корректного рендеринга `MediaRouteButton`):

```xml
<!-- res/values/themes.xml -->
<style name="Theme.MyApp" parent="Theme.AppCompat.Light.NoActionBar"> … </style>
```

Активность должна наследоваться от `AppCompatActivity`.

**3. Разместите свою кнопку Cast:**

> **Обязательно:** для каждой `MediaRouteButton` вызовите
> `CastButtonFactory.setUpMediaRouteButton(context, button)` — это привязывает кнопку к
> Cast-селектору. **Без этого вызова диалог кнопки использует пустой селектор и показывает
> «Нет доступных устройств», хотя `CastContext` находит Chromecast.** Одной инициализации
> `CastContext` недостаточно. `CastButtonFactory` — из
> `com.google.android.gms:play-services-cast-framework` (добавьте зависимость в свой модуль).

```kotlin
// Compose — через AndroidView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

AndroidView(
    factory = { ctx ->
        MediaRouteButton(ctx).also { button ->
            CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, button)
        }
    },
    modifier = Modifier.size(48.dp),
)
```

```xml
<!-- XML -->
<androidx.mediarouter.app.MediaRouteButton
    android:layout_width="48dp"
    android:layout_height="48dp" />
```
```kotlin
// В onCreate(), после setContentView:
CastButtonFactory.setUpMediaRouteButton(applicationContext, findViewById(R.id.castButton))
```

**4. Следите за статусом каста через контроллер:**

```kotlin
val isCasting by controller.isCasting.collectAsState()
val deviceName by controller.castDeviceName.collectAsState()

if (isCasting) Text("📺 Трансляция на ${deviceName ?: "Chromecast"}")
```

Подробная документация — [docs/PLAYER-API.md → Google Cast](docs/PLAYER-API.md#google-cast).

---

## Модуль `offline-sdk`

### Подключение

```kotlin
implementation("com.boomstream:offline-sdk:1.7.0")
```

### Инициализация

```kotlin
class MyApplication : Application() {
    val offlineManager by lazy {
        BoomstreamOfflineManager(
            context = this,
            configClient = Boomstream.configClient,
        )
    }
}
```

### Загрузка видео

```kotlin
// В ViewModel или корутине Activity/Fragment:
lifecycleScope.launch {
    offlineManager.downloadVideoOffline(
        mediaCode = "Il4lNOfL",
        onProgress = { fraction -> progressBar.progress = (fraction * 100).toInt() },
    )
}
```

### Управление загрузками

```kotlin
// Приостановить (с сохранением прогресса)
offlineManager.cancelDownload("Il4lNOfL")

// Удалить загруженный файл
offlineManager.deleteDownload("Il4lNOfL")

// Отслеживать состояние через Flow
offlineManager.getDownloadState("Il4lNOfL").collect { state ->
    // DownloadState: NotDownloaded | Queued | Downloading(progress) | Completed | Failed(msg) | Removing
}
```

---

## Модуль `api-sdk`

### Подключение

```kotlin
implementation("com.boomstream:api-sdk:1.7.0")
```

### Использование Boomstream API

```kotlin
// Список медиафайлов из корневой папки
Boomstream.api.listFolder().onSuccess { items ->
    // items: List<FolderMediaItem> — code, title, duration, poster
}
Boomstream.api.listFolder().onFailure { error ->
    // error: BoomstreamApiError
}

// Список прямых эфиров
Boomstream.api.listLive().onSuccess { items -> /* List<LiveMediaItem> */ }

// Список плейлистов
Boomstream.api.listPlaylists().onSuccess { items -> /* List<PlaylistItem> */ }
```

### Config endpoint

```kotlin
// Получить конфиг медиафайла напрямую
val config = Boomstream.configClient.getConfig("Il4lNOfL").getOrThrow()
// config.mediaData: список дорожек (HLS URL, постер, длительность и т.д.)
// config.posters: постеры
```

---

## Permissions

SDK автоматически добавляет следующие разрешения через манифест-merge — вам не нужно объявлять их вручную:

| Разрешение | Модуль | Назначение |
|---|---|---|
| `INTERNET` | `:player-sdk` | HLS стриминг и запросы к config endpoint |
| `FOREGROUND_SERVICE` | `:offline-sdk` | `BoomstreamVideoDownloadService` требует foreground service на Android 9+ (API 28+) |
| `FOREGROUND_SERVICE_DATA_SYNC` | `:offline-sdk` | Область foreground service типа `dataSync` на Android 14+ (API 34+) |

Если вы используете только `:player-sdk` без `:offline-sdk`, в манифест добавляется только `INTERNET`.

**Хранилище.** SDK не запрашивает `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`. Offline-загрузки по умолчанию сохраняются во внутреннюю память приложения (`Context.getFilesDir()`), доступную только этому приложению. Для экономии внутренней памяти доступен вариант с внешним хранилищем через `BoomstreamOfflineConfig(storageLocation = StorageLocation.EXTERNAL)` — тоже без манифест-разрешений на Android 10+ (scoped storage).

---

## Configuration & secrets

### API-ключ

**Никогда** не коммитьте API-ключ в репозиторий. Используйте один из двух путей:

**Локально** (`local.properties` — в `.gitignore`):

```properties
BOOMSTREAM_API_KEY=ваш-ключ
```

`app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        val props = rootProject.file("local.properties").let {
            java.util.Properties().apply { if (it.exists()) load(it.inputStream()) }
        }
        val apiKey = props.getProperty("BOOMSTREAM_API_KEY")
            ?: System.getenv("BOOMSTREAM_API_KEY")
            ?: error("BOOMSTREAM_API_KEY missing — set it in local.properties or as an env var. See README §Configuration & secrets.")
        buildConfigField("String", "BOOMSTREAM_API_KEY", "\"$apiKey\"")
    }
    buildFeatures { buildConfig = true }
}
```

**В CI** — пробрасывайте `BOOMSTREAM_API_KEY` как secret env. Тот же `buildConfigField` подхватит его из `System.getenv()`.

> ❌ **Anti-pattern:** `object BoomstreamConfig { const val API_KEY = "…" }` — попадает в VCS-историю, видно всем с git-доступом, может утечь через публичный fork репозитория.

> ⚠️ **Что `local.properties` + `BuildConfig` НЕ делает.** `BuildConfig.BOOMSTREAM_API_KEY` всё равно компилируется в `classes.dex` как `public static final String` — `apktool` / `jadx` извлекают его из шипнутого APK так же легко, как из hardcoded `const val`. `local.properties` защищает **от VCS-leak**, не от APK-extraction. Для Boomstream API-ключей v1.x это приемлемо (scope ключа — чтение публичного контента + квоты per-caller); для любых high-trust ключей в будущем — гоняйте вызов через серверный прокси или short-lived token exchange, а не embed в клиент.

### Custom User-Agent

> ⚠️ **`BoomstreamOptions.userAgent` устарел (deprecated).** Если вы использовали его для передачи Clear Key DRM / `ua_allow` токена — перейдите на `BoomstreamOptions.userAgentToken` (см. раздел выше). Поле `userAgent` будет удалено в 2.0.

SDK по умолчанию использует `User-Agent: Boomstream Android SDK v<version>` (с токеном, если задан `userAgentToken`). Если нужен полностью кастомный UA-строкой — `userAgent` переопределяет его целиком:

```kotlin
Boomstream.init(
    context = this,
    apiKey = BuildConfig.BOOMSTREAM_API_KEY,
    options = BoomstreamOptions(
        @Suppress("DEPRECATION")
        userAgent = "BoomstreamSDK/1.0 MyApp/2.3",
    ),
)
```

> ⚠️ **Ключи в User-Agent.** `User-Agent` логируется nginx/Apache access-logs, CDN (CloudFront/Cloudflare) и корпоративными прокси. Не передавайте в `userAgent` секреты, которые не должны попасть в эти log sinks. Для токена Clear Key DRM используйте `BoomstreamOptions.userAgentToken` — он управляет UA и для конфиг-запроса, и для сегментных запросов.

---

## API references

| Ресурс | URL | Назначение |
|---|---|---|
| Boomstream API Explorer | <https://api.boomstream.com/> | Документация реализованных ручек — потребляется `:api-sdk`. Реализовано: получение списка видео из папки, списка трансляций из папки, списка плейлистов. Остальные endpoints — по запросу в [Support](https://boomstream.com). |
| Config endpoint | `https://play.boomstream.com/{mediaCode}/config` | JSON c `mediaData.links.hls`, `posters`, настройками плеера. Использует `:player-sdk` под капотом — обычно вам напрямую не нужен. |

---

## Distribution

Исходный код — [github.com/boomstream-com/boomstream-android-sdk](https://github.com/boomstream-com/boomstream-android-sdk).

Артефакты публикуются в **Maven Central** (`com.boomstream:*`) начиная с v1.0. Дополнительных репозиториев и токенов не нужно.

Версионирование — [SemVer](https://semver.org/). Breaking changes только в major-релизах. Детали релизов — в [CHANGELOG.md](CHANGELOG.md).

Лицензия — **Apache 2.0**. Позволяет использовать SDK в проприетарных приложениях без открытия исходников.

---

## Сборка и тестирование

Полная инструкция по сборке + запуск на эмуляторе (macOS / Windows / Linux) и на физическом устройстве — в [BUILD.md](BUILD.md).

TL;DR:

```bash
./gradlew :example-app:installDebug   # собрать и поставить демо на подключённое устройство
./gradlew test                        # unit-тесты всех модулей
./gradlew connectedAndroidTest        # инструментальные тесты (требует устройство/эмулятор)
```

---

## Contributing

Issues и pull requests — в репозитории на [GitHub](https://github.com/boomstream-com/boomstream-android-sdk). По другим вопросам — [boomstream.com](https://boomstream.com).

Перед PR:

1. `./gradlew check` — линтеры + unit-тесты должны пройти.
2. Code review — минимум один approve от owner'а модуля (см. `CODEOWNERS`).
3. CI — зелёный на feature-ветке.

---

## License

```
Copyright 2026 Boomstream

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Полный текст — [LICENSE](LICENSE).

---
---

# Boomstream Android SDK (English)

Native Android SDK for embedding the Boomstream player, offline downloads, and Boomstream API access. Built on [Media3 / ExoPlayer](https://developer.android.com/media/media3) + Jetpack Compose. Ships as separate Maven modules — pull in only what you need.

Source code on [GitHub](https://github.com/boomstream-com/boomstream-android-sdk). Artifacts published to Maven Central.

## Quick start

**1. Ensure Maven Central is in your repositories** (`settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

**Both** repositories are required: `mavenCentral()` serves the `com.boomstream:*` modules, while `google()` provides the transitive dependencies (Jetpack Compose, Media3). Both are already present in standard Android projects; no extra repositories or tokens are needed.

**2. Add the dependencies** to `app/build.gradle.kts`:

```kotlin
implementation("com.boomstream:player-sdk:1.7.0")
implementation("com.boomstream:api-sdk:1.7.0")
implementation("com.boomstream:offline-sdk:1.7.0") // optional
```

**3. Initialize** in `Application.onCreate()`:

```kotlin
Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY)
```

The API key is used only by `api-sdk` features (folder/live/playlist listing). If you only embed the player or use offline downloads, no API calls are made. The key must come from `local.properties` (gitignored) or a CI secret — never a hardcoded constant.

**4. Show the player**:

```kotlin
@Composable
fun MyScreen() {
    BoomstreamPlayer(
        mediaCode = "Il4lNOfL",
        configClient = Boomstream.configClient,
    )
}
```

The composable fetches the config from `play.boomstream.com/{mediaCode}/config`, resolves the HLS manifest and poster, and plays. Arrays in `mediaData` are treated as playlists. Missing `mediaData` falls back to a poster-only state.

A View-based equivalent is available as `BoomstreamPlayerView`.

## Modules

| Module | Maven artifact | Purpose |
|---|---|---|
| `:api-sdk` | `com.boomstream:api-sdk` | Type-safe Kotlin client for [api.boomstream.com](https://api.boomstream.com/) + the player config endpoint |
| `:player-sdk` | `com.boomstream:player-sdk` | Media3-based player (Composable + View), playlist, poster fallback |
| `:offline-sdk` | `com.boomstream:offline-sdk` | HLS offline downloads via Media3 `DownloadService` |
| `:example-app` | (not published) | Demo APK exercising all three modules |

## Per-module quick starts

### player-sdk

```kotlin
// Compose — streaming
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
)

// ua_allow / Clear Key DRM — set token once at init; no per-call parameter needed
// (BoomstreamOptions.userAgentToken covers both config and segment requests)
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    // token flows automatically from BoomstreamOptions.userAgentToken set in Boomstream.init()
)

// Offline playback (requires offline-sdk)
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
)

// Locale override (BCP 47 tag)
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    locale = "ru",
)

// Programmatic playback control, events, and quality selection (v1.2.0+ / v1.5.0+)
val controller = rememberBoomstreamPlayerController()
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    controller = controller,
    // Optional: "Quality" row in the player settings panel ⚙ (default off)
    advancedOptions = AdvancedPlayerOptions(enableQualitySelector = true),
)
controller.selectQuality(VideoQuality.Resolution(height = 720))
controller.selectAuto()
// Full reference — see docs/PLAYER-API.md
```

For programmatic playback control, event observation, quality selection, and styling —
see [docs/PLAYER-API.md](docs/PLAYER-API.md).

### Styling / theming

Available in v1.6.0+. Pass `BoomstreamPlayerStyle` to customise the player's visual appearance. All fields are nullable — `null` keeps the SDK default for that colour.

```kotlin
// Compose — style parameter
val brandViolet = Color(0xFF662BFF).toArgb()
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    style = BoomstreamPlayerStyle(
        loaderColor          = brandViolet,
        accentColor          = brandViolet,   // control button tint (best-effort)
        seekBarPlayedColor   = brandViolet,
        seekBarScrubberColor = brandViolet,
    ),
)
```

```xml
<!-- View — XML attributes -->
<com.boomstream.sdk.player.BoomstreamPlayerView
    xmlns:app="http://schemas.android.com/apk/res-auto"
    app:boomstreamLoaderColor="@color/brand_violet"
    app:boomstreamAccentColor="@color/brand_violet"
    app:boomstreamSeekBarPlayedColor="@color/brand_violet"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
// View — programmatic (applies immediately, no reload needed)
player.setLoaderColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setAccentColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setSeekBarPlayedColor(ContextCompat.getColor(this, R.color.brand_violet))
```

Full field reference — [docs/PLAYER-API.md → Styling / theming](docs/PLAYER-API.md#styling--theming).

### Google Cast (v1.7.0+)

> **v1 limitation — unprotected content only.** Projects with download protection enabled cannot
> cast in v1. DRM-protected Cast (custom receiver) is planned for a future release.

**1. Declare the options provider in your manifest:**

```xml
<!-- AndroidManifest.xml -->
<application …>
    <meta-data
        android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
        android:value="com.boomstream.sdk.player.BoomstreamCastOptionsProvider" />
</application>
```

**2. Use an AppCompat activity and theme** (required by `MediaRouteButton`):

```xml
<!-- res/values/themes.xml -->
<style name="Theme.MyApp" parent="Theme.AppCompat.Light.NoActionBar"> … </style>
```

Your activity must extend `AppCompatActivity`.

**3. Place your own Cast button:**

> **Required:** call `CastButtonFactory.setUpMediaRouteButton(context, button)` on every
> `MediaRouteButton`. Without it the button's chooser uses an empty selector and shows
> "No devices available" even though `CastContext` discovers nearby Chromecasts — initialising
> `CastContext` alone does not configure the button. `CastButtonFactory` is from
> `com.google.android.gms:play-services-cast-framework` (add it to your app module).

```kotlin
// Compose
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

AndroidView(
    factory = { ctx ->
        MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) }
    },
    modifier = Modifier.size(48.dp),
)
```

```xml
<!-- XML layout -->
<androidx.mediarouter.app.MediaRouteButton
    android:layout_width="48dp"
    android:layout_height="48dp" />
```

**4. Observe cast state:**

```kotlin
val isCasting by controller.isCasting.collectAsState()
val deviceName by controller.castDeviceName.collectAsState()

if (isCasting) Text("Casting to ${deviceName ?: "Chromecast"}")
```

Full setup guide — [docs/PLAYER-API.md → Google Cast](docs/PLAYER-API.md#google-cast).

### offline-sdk

```kotlin
val offlineManager = BoomstreamOfflineManager(
    context = applicationContext,
    configClient = Boomstream.configClient,
)

// Download
lifecycleScope.launch {
    offlineManager.downloadVideoOffline("Il4lNOfL") { progress -> /* 0.0..1.0 */ }
}

// Observe state
offlineManager.getDownloadState("Il4lNOfL").collect { state ->
    // NotDownloaded | Queued | Downloading(progress) | Completed | Failed(msg) | Removing
}

// Delete
offlineManager.deleteDownload("Il4lNOfL")
```

### api-sdk

```kotlin
// Folder listing
Boomstream.api.listFolder().onSuccess { items -> /* List<FolderMediaItem> */ }

// Live streams
Boomstream.api.listLive().onSuccess { items -> /* List<LiveMediaItem> */ }

// Playlists
Boomstream.api.listPlaylists().onSuccess { items -> /* List<PlaylistItem> */ }
```

## Protected content (ua_allow) / Clear Key DRM

If your Boomstream project enables **«Защита → Разрешить User-Agent, игнорируя защиту от рестримминга»** (`ua_allow`), both the config endpoint and HLS media server validate the request `User-Agent` against a token via a case-insensitive substring check. Set the token **once** at SDK init time via `BoomstreamOptions.userAgentToken`; both the config client and segment/manifest client pick it up automatically:

```kotlin
Boomstream.init(
    this,
    BuildConfig.BOOMSTREAM_API_KEY,
    BoomstreamOptions(
        userAgentToken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
    ),
)
```

After that, `BoomstreamPlayer` and `BoomstreamPlayerView.load()` require no additional parameters:

```kotlin
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
)
```

`BOOMSTREAM_DRM_TOKEN` comes from `local.properties` or a CI secret — **never** hardcode this value. **Do not log `userAgentToken`.**

**Security.**
- `User-Agent` is captured by nginx/CDN/corporate-proxy access logs. Treat the `ua_allow` token as a low-trust shared secret (bypass of referer protection), not a full auth token. For high-trust scenarios, use a server-side proxy.
- `BoomstreamOptions.toString()` masks both `userAgent` and `userAgentToken` as `***` so accidental `Log.d(opts.toString())` calls do not leak to logcat.

### Migration from `allowClearKeyDRMtoken` / `options.userAgent`

If you already use the old workaround, it continues to work unchanged — no code changes required:

```kotlin
// Old approach — still works, migrate at your convenience
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    allowClearKeyDRMtoken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
)
```

`options.userAgent` in `BoomstreamOptions` is `@Deprecated` — move the token to `userAgentToken` when convenient. The field will be removed in 2.0.

## Permissions

The SDK merges the following permissions into your app manifest automatically — no manual declaration needed:

| Permission | Module | Required for |
|---|---|---|
| `INTERNET` | `:player-sdk` | HLS streaming and config endpoint requests |
| `FOREGROUND_SERVICE` | `:offline-sdk` | `BoomstreamVideoDownloadService` on Android 9+ (API 28+) |
| `FOREGROUND_SERVICE_DATA_SYNC` | `:offline-sdk` | Scopes the foreground service type to `dataSync` on Android 14+ (API 34+) |

If you only use `:player-sdk`, only `INTERNET` is added.

**Storage.** The SDK does not request `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. Downloads are stored in the app's internal storage (`Context.getFilesDir()`) by default, scoped to the app. Pass `BoomstreamOfflineConfig(storageLocation = StorageLocation.EXTERNAL)` to use external storage (`Context.getExternalFilesDir()`), which also requires no manifest permission on Android 10+ (scoped storage).

## API references

- Boomstream API Explorer — <https://api.boomstream.com/> — implemented endpoints: folder listing, live streams listing, playlists listing. Additional endpoints available on request via [Support](https://boomstream.com).
- Player config endpoint — `https://play.boomstream.com/{mediaCode}/config`

## Distribution

Source code — [github.com/boomstream-com/boomstream-android-sdk](https://github.com/boomstream-com/boomstream-android-sdk).

Artifacts published to **Maven Central** (`com.boomstream:*`) from v1.0. Apache 2.0.

## Build & test

See [BUILD.md](BUILD.md) for emulator / device setup on macOS, Windows, and Linux.

```bash
./gradlew :example-app:installDebug
./gradlew test
./gradlew connectedAndroidTest
```

## Contributing

Issues and pull requests — [github.com/boomstream-com/boomstream-android-sdk](https://github.com/boomstream-com/boomstream-android-sdk). For other questions, reach out via [boomstream.com](https://boomstream.com).

Before submitting a PR:
1. `./gradlew check` — linters and unit tests must pass.
2. At least one approve from a module owner (see `CODEOWNERS`).
3. CI must be green on your feature branch.

## License

Apache 2.0 — see [LICENSE](LICENSE). Suitable for use in proprietary apps without source disclosure.

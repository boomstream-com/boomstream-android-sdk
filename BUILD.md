# Build & run — Boomstream Android SDK

Гайд по локальной сборке монорепо и запуску `:example-app` (когда он появится) на эмуляторе и физическом устройстве. Покрывает macOS и Windows; команды для macOS проверены на собственной среде разработчика, команды для Windows документированы по официальной документации Android и Gradle.

> Если что-то в этом гайде расходится с реальностью после очередного bump'а версий — открой issue/PR. Ground truth по версиям инструментов — `gradle/libs.versions.toml` + `gradle/wrapper/gradle-wrapper.properties`.

---

## 1. Требования к окружению

| Компонент | Версия | Обоснование |
|---|---|---|
| **JDK** | 17 (LTS) | Android Gradle Plugin 8.7.x требует JDK 17 минимум. JDK 21 тоже поддерживается, но 17 — рекомендуемый LTS для AGP 8.x. |
| **Gradle** | 8.10.2 | Зафиксировано в `gradle/wrapper/gradle-wrapper.properties`. Поднимается автоматически через `./gradlew`. |
| **Android Gradle Plugin** | 8.7.3 | `gradle/libs.versions.toml` → `agp`. |
| **Kotlin** | 2.1.0 | `gradle/libs.versions.toml` → `kotlin`. |
| **Android Studio** | Ladybug (2024.2.1) или новее | Минимум для AGP 8.7. |
| **Android SDK Platform** | 34 (compileSdk), 35 опционально | Устанавливается через Android Studio SDK Manager. |
| **Android SDK Build-Tools** | 34.0.0+ | Подтягивается AGP автоматически при первой сборке. |
| **Android SDK Platform-Tools** | latest | Даёт `adb`. Без него запуск/отладка невозможны. |
| **Android Emulator** | latest | Опционально — можно использовать физическое устройство. |
| **Boomstream API key** | — | Нужен только для запуска `:example-app` и интеграционных тестов SDK против реального API. Кладётся в `local.properties` (см. §6). |

> ⚠️ **JDK 8 не подойдёт.** Если `java -version` возвращает `1.8.x`, AGP 8.7 откажется собирать с сообщением «requires Java 17 or higher». Установка JDK 17 — первый шаг.

---

## 2. Setup — macOS (Apple Silicon и Intel)

### 2.1. Установить JDK 17

Любой из путей:

**Вариант A — через [SDKMAN](https://sdkman.io/) (рекомендуется для разработчиков, переключающих JDK между проектами):**

```bash
curl -s "https://get.sdkman.io" | bash
exec "$SHELL"  # подтянуть sdkman в текущий shell
sdk install java 17.0.12-oracle
sdk default java 17.0.12-oracle
java -version  # должно показать openjdk version "17.x.x"
```

**Вариант B — через Homebrew (если SDKMAN не используется):**

```bash
brew install openjdk@17
# openjdk@17 устанавливается keg-only — нужно явное symlink-добавление в системную папку:
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
# (Intel Mac → путь /usr/local/opt/openjdk@17/...)
```

Выставить `JAVA_HOME`. Добавь в `~/.zshrc` (или `~/.bash_profile`):

```bash
# SDKMAN-вариант:
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"

# Homebrew-вариант (Apple Silicon):
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"

# Homebrew-вариант (Intel):
export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"

export PATH="$JAVA_HOME/bin:$PATH"
```

После перезапуска shell: `java -version` → `openjdk 17.x.x`.

### 2.2. Установить Android Studio + SDK

1. Скачать [Android Studio](https://developer.android.com/studio) (Ladybug или новее), перетащить в `/Applications`.
2. Первый запуск → Setup Wizard:
   - выбрать **Standard** install,
   - дождаться скачивания **Android SDK Platform 34** + **Build-Tools 34.0.0** + **Platform-Tools** + **Android Emulator** (~6 ГБ).
3. После окончания мастера: **Tools → SDK Manager** → проверить, что отмечены:
   - `Android 14 (API 34)` (или 35 если работаешь с новейшим)
   - `Android SDK Platform-Tools`
   - `Android Emulator`
   - `Android SDK Build-Tools 34.0.0`

SDK по умолчанию ставится в `~/Library/Android/sdk`.

### 2.3. Выставить `ANDROID_HOME` + добавить `adb` / `emulator` в PATH

В `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"  # дубликат: некоторые инструменты читают именно SDK_ROOT
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Перезапустить shell. Проверка:

```bash
adb version       # Android Debug Bridge version 1.0.41
emulator -version # Android emulator version 35.x.x
```

### 2.4. (Опционально) Поставить эмулятор из CLI без Android Studio

Если нужен headless-setup (CI runner, удалённая машина):

```bash
# cmdline-tools поставлены вместе с Android Studio либо отдельно через
# https://developer.android.com/studio#command-tools
sdkmanager --install "platforms;android-34" "system-images;android-34;google_apis;arm64-v8a" "emulator" "platform-tools"
sdkmanager --licenses  # принять все
avdmanager create avd -n Pixel_API_34 -k "system-images;android-34;google_apis;arm64-v8a" -d pixel_6
```

> На Intel Mac выбирай `system-images;android-34;google_apis;x86_64` вместо `arm64-v8a`.

---

## 3. Setup — Windows 10/11

Команды ниже не верифицированы на собственной среде (TL подтверждает по официальной документации Google и брифу); если найдёшь расхождение — PR welcome.

### 3.1. Установить JDK 17

Любой из путей:

- **Eclipse Temurin (рекомендуется):** скачать MSI с [adoptium.net](https://adoptium.net/temurin/releases/?version=17), установить, инсталлер сам пропишет `JAVA_HOME` если поставить галку «Set JAVA_HOME variable».
- **Microsoft Build of OpenJDK:** [microsoft.com/openjdk](https://learn.microsoft.com/en-us/java/openjdk/download).
- **Chocolatey:**
  ```powershell
  choco install temurin17
  ```

После установки в PowerShell:

```powershell
java -version  # openjdk 17.x.x
echo $env:JAVA_HOME
```

Если `JAVA_HOME` не выставился — `System Properties → Environment Variables → New User Variable → JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`. Добавить `%JAVA_HOME%\bin` в `Path`.

### 3.2. Установить Android Studio + SDK

1. Скачать установщик с [developer.android.com/studio](https://developer.android.com/studio).
2. Setup Wizard → Standard install → дождаться `Platform 34` + `Build-Tools` + `Platform-Tools` + `Emulator`.

SDK по умолчанию ставится в `%LOCALAPPDATA%\Android\Sdk` (т.е. `C:\Users\<You>\AppData\Local\Android\Sdk`).

### 3.3. Выставить `ANDROID_HOME` + PATH

PowerShell (от имени пользователя, не админа — записывает в User-scope env):

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "$env:LOCALAPPDATA\Android\Sdk", "User")
$paths = @(
  "$env:LOCALAPPDATA\Android\Sdk\platform-tools",
  "$env:LOCALAPPDATA\Android\Sdk\emulator",
  "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin"
)
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$currentPath;$($paths -join ';')", "User")
```

Открыть **новый** PowerShell (старая сессия не подхватит). Проверка:

```powershell
adb version
emulator -version
```

### 3.4. Включить аппаратную виртуализацию для эмулятора

Эмулятор требует аппаратной виртуализации:

- **Windows 11 / 10 build 19041+** — установится **WHPX** (Windows Hypervisor Platform). Это рекомендуемый путь:
  - В Windows Features (`optionalfeatures.exe`) включи **Windows Hypervisor Platform** + **Virtual Machine Platform**, перезагрузись.
- **Старые Intel-машины без WHPX** — Android Studio предложит установить **Intel HAXM** через SDK Manager. На современных машинах WHPX предпочтительнее.
- **AMD-машины** — обязателен WHPX (HAXM AMD не поддерживает).

Проверить виртуализация в BIOS: Intel VT-x / AMD-V должны быть включены.

---

## 4. Сборка из CLI

Все команды запускаются из корня репозитория. `./gradlew` поднимет нужный Gradle 8.10.2 при первом запуске.

> На Windows вместо `./gradlew` пиши `gradlew.bat` (или `.\gradlew.bat` в PowerShell).

### 4.1. Полная сборка всех модулей

```bash
./gradlew build
```

Чистая сборка (если что-то залипло):

```bash
./gradlew clean build
```

### 4.2. Локальная публикация SDK-артефактов в `~/.m2`

Удобно когда хочешь подёргать SDK из соседнего проекта без выкатки в реальный maven repo:

```bash
./gradlew :api-sdk:publishToMavenLocal
# по мере появления модулей в Wave-2/3:
./gradlew :player-sdk:publishToMavenLocal
./gradlew :offline-sdk:publishToMavenLocal
```

В потребляющем проекте подключить `mavenLocal()` в `settings.gradle.kts` (или `build.gradle.kts`) и зависимость на свежеопубликованную версию (см. `gradle/libs.versions.toml`).

### 4.3. Установить `:example-app` на подключённое устройство / эмулятор

```bash
./gradlew :example-app:installDebug
```

После успешной установки приложение появится в лаунчере как `Boomstream Example`. Запуск из CLI:

```bash
adb shell am start -n com.boomstream.example/.MainActivity
```

Объединённая команда:

```bash
./gradlew :example-app:installDebug && \
  adb shell am start -n com.boomstream.example/.MainActivity
```

> Пакет/Activity имя — уточняй по `:example-app/src/main/AndroidManifest.xml`.

### 4.4. Запуск тестов

```bash
./gradlew test                        # unit (JVM) тесты всех модулей
./gradlew :api-sdk:testDebugUnitTest  # точечно
./gradlew connectedAndroidTest        # инструментированные (требуют подключённого устройства/эмулятора)
```

### 4.5. Lint

```bash
./gradlew lint
# отчёты: <module>/build/reports/lint-results-debug.html
```

---

## 5. Запуск на эмуляторе

### 5.1. Создать AVD (если ещё нет)

Через **Android Studio**: `Tools → Device Manager → Create Device`. Рекомендуемый baseline для разработки — **Pixel 6 + API 34 (Google APIs, ABI native под твою машину)**.

Через **CLI**:

```bash
avdmanager create avd -n Pixel_API_34 \
  -k "system-images;android-34;google_apis;arm64-v8a" \
  -d pixel_6
```

### 5.2. Запустить эмулятор

```bash
emulator -list-avds
# Pixel_API_34
emulator -avd Pixel_API_34
```

Эмулятор стартанёт в отдельном окне. После загрузки `adb devices` покажет:

```
List of devices attached
emulator-5554   device
```

### 5.3. Установить + запустить `:example-app`

```bash
./gradlew :example-app:installDebug
adb shell am start -n com.boomstream.example/.MainActivity
```

### 5.4. Headless / cold-boot

Для CI или быстрой проверки без UI:

```bash
emulator -avd Pixel_API_34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
```

---

## 6. Запуск на физическом устройстве

### 6.1. Включить USB Debugging

На устройстве:

1. **Settings → About phone** → 7 раз тапнуть по **Build number** → появится «You are now a developer».
2. **Settings → System → Developer options** → включить **USB debugging**.
3. (Опционально) **USB debugging (Security settings)** + **Wireless debugging** для Android 11+ debug-через-Wi-Fi.

### 6.2. Подключить и доверить хосту

Подключи USB-кабелем. На устройстве появится popup «Allow USB debugging from this computer?» → **Always allow + OK**.

```bash
adb devices
# должно показать:
# List of devices attached
# R5CR21XXXXX  device
```

Если устройство показано как `unauthorized` — отклонил popup, отключи/подключи кабель и подтверди заново.

Если устройство вообще не появилось — см. §8 troubleshooting.

### 6.3. Установить + запустить

```bash
./gradlew :example-app:installDebug && \
  adb shell am start -n com.boomstream.example/.MainActivity
```

Логи:

```bash
adb logcat -s "Boomstream:*"  # фильтр по тегу
```

### 6.4. (Опционально) Wireless debugging

После однократного USB-pairing:

```bash
# на устройстве: Developer options → Wireless debugging → Pair device with pairing code
adb pair <device-ip>:<pair-port>      # вводишь pairing code с экрана устройства
adb connect <device-ip>:<connect-port>
adb devices  # видим device по IP вместо serial
```

---

## 7. Локальные secrets — `local.properties`

`local.properties` хранит локальные пути и API-ключи, которые **не должны** попадать в git. Файл уже добавлен в `.gitignore`.

### 7.1. Заполнить

В корне репо есть `local.properties.example`. Скопируй и заполни:

```bash
cp local.properties.example local.properties
$EDITOR local.properties
```

```properties
# Android SDK path. Если Android Studio установлен — он сам создаст этот файл и впишет sdk.dir.
# Можешь подставить вручную, если собираешь без Studio.
sdk.dir=/Users/<you>/Library/Android/sdk           # macOS
# sdk.dir=C\:\\Users\\<You>\\AppData\\Local\\Android\\Sdk  # Windows (двойной \ + escape :)

# API key для :example-app и интеграционных тестов.
# Получить ключ — у админа Boomstream (project owner) или через панель Back Office.
BOOMSTREAM_API_KEY=your_api_key_here
```

### 7.2. Как `local.properties` читается из Gradle

Gradle сам читает `sdk.dir` через AGP, ничего настраивать не нужно.

`BOOMSTREAM_API_KEY` пробрасывается в код через `buildConfigField` или resource (см. `:example-app/build.gradle.kts`, когда модуль появится). Шаблон:

```kotlin
// in :example-app/build.gradle.kts
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    defaultConfig {
        buildConfigField(
            "String",
            "BOOMSTREAM_API_KEY",
            "\"${localProps.getProperty("BOOMSTREAM_API_KEY") ?: ""}\""
        )
    }
    buildFeatures {
        buildConfig = true
    }
}
```

В коде:

```kotlin
Boomstream.init(applicationContext, BuildConfig.BOOMSTREAM_API_KEY)
```

> ⚠️ Если `BOOMSTREAM_API_KEY` не задан, `:example-app` соберётся (поле станет пустой строкой), но runtime инициализация SDK упадёт. Для CI/headless-сборок без реального API — отдельный flavor `noKey` или фиктивный ключ; реализуется когда понадобится.

### 7.3. Не коммитить `local.properties`

Файл в `.gitignore`. Если случайно `git add local.properties` — `git restore --staged local.properties` сразу же. Случайный коммит ключа → ротация ключа через Back Office + `git filter-repo` для удаления из истории (escalate to TL + CSO).

---

## 8. Troubleshooting

### 8.1. Gradle daemon виснет / ест память

```bash
./gradlew --stop                                       # убить все daemons
rm -rf ~/.gradle/caches/modules-2/modules-2.lock        # снять lock
./gradlew clean
```

Если регулярно — увеличить heap в `gradle.properties` корня репо:

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
```

### 8.2. `error: invalid source release: 17`

`JAVA_HOME` указывает на JDK 8 / 11. Проверь `echo $JAVA_HOME` + `java -version`. Перезапусти shell после правки `~/.zshrc`. На macOS глобальный `java` может перекрываться `/usr/libexec/java_home` — проверь `/usr/libexec/java_home -v 17`.

### 8.3. Эмулятор не стартует / зависает на boot

```bash
# Сбросить состояние AVD
emulator -avd Pixel_API_34 -wipe-data

# Если KVM/HAXM/WHPX не активен — увидишь "no accelerator found"
# macOS: убедись что включён Hypervisor.framework (по умолчанию on на M-series)
# Windows: см. §3.4
```

### 8.4. `adb devices` пусто или `unauthorized`

```bash
adb kill-server
adb start-server
adb devices
```

Если всё ещё пусто:

- **macOS:** проверь USB-кабель (часть кабелей — только charge, без data lines). Поменяй порт.
- **Windows:** убедись, что установлен **Google USB Driver** (через SDK Manager → SDK Tools). Без него Windows не распознаёт устройство в `adb`-режиме. Для Samsung — нужны их Samsung USB Driver. Перезагрузка после установки драйвера обязательна.

Если `unauthorized` — на устройстве отвергнут popup доверия. Отключить-подключить, подтвердить «Always allow».

### 8.5. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` или signature mismatch

Уже установлена сборка с другой подписью (например, release APK или сборка от другого разработчика):

```bash
adb uninstall com.boomstream.example
./gradlew :example-app:installDebug
```

### 8.6. `Could not resolve all dependencies` / артефакт не находится

```bash
# Очистить локальные кэши
rm -rf ~/.gradle/caches/modules-2/
./gradlew --refresh-dependencies build
```

Проверь VPN/прокси — `mavenCentral()` и `google()` должны быть доступны. Корпоративный mirror (если есть) добавляется в `settings.gradle.kts` → `pluginManagement.repositories` + `dependencyResolutionManagement.repositories`.

### 8.7. AGP жалуется на минимальную версию Android Studio

`AGP 8.7.x requires Studio Ladybug (2024.2.1) or newer.` Обнови Android Studio через Help → Check for Updates.

### 8.8. `License for package Android SDK Build-Tools NN.N.N not accepted`

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
# нажимай y до конца
```

На Windows:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

---

## 9. CI / автоматизированная сборка

Для CI ещё нет настроенного pipeline'а (отдельный sub-issue, см. parent epic). Шаблон минимально работающего job'а (GitLab CI / GitHub Actions):

```yaml
# GitHub Actions example
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: android-actions/setup-android@v3
      - run: ./gradlew build --no-daemon
```

`ANDROID_HOME` подставляется `setup-android` action'ом автоматически. `BOOMSTREAM_API_KEY` для CI — отдельный secret (см. CI sub-issue).

---

## 10. Где искать дальше

- `gradle/libs.versions.toml` — все версии зависимостей и плагинов в одном месте.
- `settings.gradle.kts` — список модулей в монорепо.
- `README.md` — обзор репо + entry points.
- Ground truth для всех JDK/AGP-версий — `libs.versions.toml`.

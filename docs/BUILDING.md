# Розробка та складання

## Інструменти

- JDK 17.
- Android SDK Platform 35.
- Gradle Wrapper із репозиторію (Gradle 8.13) та Android Gradle Plugin 8.13.2.

Код застосунку — Java 17. Окремих бібліотек виконання в Gradle-залежностях немає; бот використовує Android API.

## Debug-збірка

У Windows PowerShell, з кореня репозиторію:

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

Зібраний файл:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Для встановлення через ADB на під’єднаний пристрій:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Обидві збірки підписуються одним збереженим ключем із локального файла
`local-signing.properties`. Ключ не генерується під час збірки. Якщо конфігурації
немає, Gradle зупиняється, щоб випадково не підписати оновлення іншим ключем.
Файл конфігурації та keystore ігноруються Git. Поля конфігурації:

```properties
storeFile=/absolute/path/to/persistent-signing.jks
storePassword=YOUR_EXISTING_PASSWORD
keyAlias=YOUR_EXISTING_ALIAS
keyPassword=YOUR_EXISTING_PASSWORD
storeType=JKS
```

Для оновлення потрібні та сама пара package ID / ключ. Debug має package ID
`ua.borlock.autotap.newkey` і назву з позначкою «нова»; release — `ua.borlock.autotap`.

## Структура

```text
app/src/main/java/ua/borlock/autotap/   Java-код інтерфейсу, розпізнавання та розв’язувача
app/src/main/res/                       ресурси Android і конфігурація AccessibilityService
docs/                                   інструкції користувача й архітектурні нотатки
gradle/wrapper/                         версія Gradle Wrapper
```

`local.properties`, згенеровані каталоги `build/` і `.gradle/`, APK та прикріплені до Codex матеріали ігноруються Git. Не додавай у коміт XML-дамп налаштувань Android із кореня проєкту.

## Поточна конфігурація

- `applicationId`: `ua.borlock.autotap`.
- Версія застосунку: `1.9.43` (`versionCode` 74).
- `minSdk`: 24; для знімків екрана AccessibilityService потрібен Android 11 / API 30 або новіший.
- `targetSdk` / `compileSdk`: 35.

## Перевірки

```bash
bash gradlew testDebugUnitTest --no-daemon --max-workers=1
bash tools/check-merge-madness.sh
```

Другій команді потрібні `JAVA_HOME` і `ANDROID_HOME` або `ANDROID_SDK_ROOT`.
Вона запускає код розпізнавача, скомпільований для APK: звіряє всі клітинки
з вручну перевіреними матрицями скриншотів, відхиляє перекриті/обрізані кадри,
перевіряє масштабування й навчання об'єктів із підказок без статичних зразків.
Окремі штучні сценарії перевіряють відкриття нового силуету та технічну
місткість позицій 1–17 у чотирьох рядках. Фактичних рівнів гри — 15;
ці штучні зразки не підтверджують розпізнавання всіх реальних пізніх об'єктів.
Тести JVM не виконують фізичний жест на Android; його перевіряють на телефоні.

Тести таймерів використовують Robolectric 4.14.1 з Android API 33;
`unitTests.includeAndroidResources` увімкнено. Перший запуск завантажує
залежності Gradle та образ Android для Robolectric і потребує мережі.
У середовищі з обов’язковим HTTP-проксі передай його налаштування JVM
згідно з політикою середовища. Ключ підпису потрібен і для конфігурації тестів;
не створюй новий ключ для виправлення відсутньої локальної конфігурації.

Для APK, що оновлює основний застосунок, використовуй release:

```bash
bash gradlew testDebugUnitTest assembleRelease --no-daemon --max-workers=1
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Перед поширенням звіряй package ID та SHA-256 сертифіката з попереднім APK,
а `versionCode` збільшуй. Debug із суфіксом `.newkey` не оновлює release.

Заміри прискорення та порівняння з попереднім рушієм описані в
[VERIFICATION-1.9.35.md](VERIFICATION-1.9.35.md).

Модульний каталог і налаштування описані в [MODULES.md](MODULES.md),
реалізований інтерфейс — у [INTERFACE-1.9.37.md](INTERFACE-1.9.37.md),
уточнене керування панеллю — у [INTERFACE-1.9.38.md](INTERFACE-1.9.38.md),
компактніші кнопки та перевірка алгоритмів — у [ALGORITHMS-1.9.39.md](ALGORITHMS-1.9.39.md),
ціль автогри «Рюкзак-Бліц» — у [BLITZ-AUTOPLAY-1.9.40.md](BLITZ-AUTOPLAY-1.9.40.md),
прискорення пошуку й точніші каскади — у [BLITZ-AUTOPLAY-1.9.41.md](BLITZ-AUTOPLAY-1.9.41.md),
окреме вікно нагород і перевірки таймерів — у [TIMERS-1.9.42.md](TIMERS-1.9.42.md),
українська/чеська локалізація — у [LOCALIZATION-1.9.43.md](LOCALIZATION-1.9.43.md),
уточнення цілі до рівня 15 — у [ALGORITHMS-LEVEL15.md](ALGORITHMS-LEVEL15.md),
ідеї наступних клікерів — у [ROADMAP.md](ROADMAP.md).

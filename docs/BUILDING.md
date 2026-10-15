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

Debug APK підписується debug-ключем Gradle. Налаштування підпису release-збірки в цьому репозиторії не задане.

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
- Версія застосунку: `1.9.16` (`versionCode` 47).
- `minSdk`: 24; для знімків екрана AccessibilityService потрібен Android 11 / API 30 або новіший.
- `targetSdk` / `compileSdk`: 35.

Автоматизований набір тестів у репозиторії не налаштований. Команда вище збирає debug APK, але сама збірка не перевіряє роботу розпізнавання на конкретному телефоні чи версії гри.

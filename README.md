# new3

Репозиторий подготовлен для работы на ветке main. Реализация приложения и утверждённые требования ещё не добавлены.

Правила работы с приоритетами и проверками: [.github/GITHUB_WORKFLOW.md](.github/GITHUB_WORKFLOW.md).

## Android Foundation

Нативная Android-сборка Knowledge Hub AI поддерживает Android 8.0 и новее. Для сборки нужны JDK 17, Android SDK 35 и Android Build Tools 35.0.0.

```sh
./gradlew :app:assembleDebug :core:testDebugUnitTest :app:lintDebug :core:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Для инструментальных тестов подключите Android-устройство или эмулятор и выполните `./gradlew :app:connectedDebugAndroidTest :core:connectedDebugAndroidTest`.

Контракт и зависимости задач описаны в [миссии](docs/ANDROID_FOUNDATION.md). Варианты визуального направления собраны в [дизайн-пакете](DESIGN.md).

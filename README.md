# Tap-Shell: Android-обёртки для сайтов

Один код WebView-приложения собирает два APK (product flavors в `app/build.gradle.kts`):

| APK | Сайт | Название | Внешние ссылки |
|---|---|---|---|
| `deeix-ru.apk` | https://tap.teamdream.online | DEEIX-RU | блокируются |
| `openhands.apk` | https://app.all-hands.dev | OpenHands | открываются в браузере |

Общее для обоих:

- Полный экран: запускается без системных панелей, плавающая кнопка в углу переключает режим
  (выбор запоминается). Полноэкранное видео на сайте тоже работает.
- Вход сохраняется между запусками (cookies).
- Загрузка файлов на сайт и скачивание файлов (в папку «Загрузки»).
- Android 10 и новее.

OpenHands: вход через GitHub, GitLab или Bitbucket проходит внутри приложения, поэтому эти
сайты (и `id.atlassian.com`) тоже открываются в нём. Домены `*.all-hands.dev` и `*.openhands.dev`
разрешены оба: сервис постепенно переезжает на новое имя.

## Сборка

Каждый push запускает GitHub Actions, оба APK лежат в Artifacts запуска (`apks`).
Push в `main` ещё и публикует их в Releases (тег `latest`).

Иконки: `icons/<flavor>.png` (`icons/deeix.png`, `icons/allhands.png`). Если файла нет,
сборка скачивает иконку с сайта.

Подпись: фиксированный ключ `app/debug.keystore`, поэтому новые сборки ставятся
поверх старых без удаления. Только для личного использования.

## Новый сайт

Добавьте flavor в `app/build.gradle.kts` (адрес, домены, название, свой `applicationId`),
строку `<flavor> <адрес>` в шаг иконок и копирование APK в `.github/workflows/build.yml`.

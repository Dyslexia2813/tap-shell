# AGENTS.md — tap-shell (Android-оболочка DEEIX-RU)

Инструкции для ИИ-агента (OpenCode, Claude Code и т.п.). Общие правила проекта DEEIX-RU (общение с Kimi, VPS, сборка сайта) — в `AGENTS.md` и `docs/` репозитория [Dyslexia2813/deeix-ru-build](https://github.com/Dyslexia2813/deeix-ru-build). Здесь — только то, что касается оболочки.

## Что это

APK-обёртка (WebView) для сайта `https://tap.teamdream.online` (DEEIX Chat Kimi). Открывает только этот сайт по https, остальные ссылки блокирует. Возможности описаны в `README.md`.

- Весь код — один `app/src/main/java/online/teamdream/tap_shell/MainActivity.java` и разметка `app/src/main/res/layout/activity_main.xml`.
- Две плавающие круглые кнопки: «Обновить» (`web.reload`) и «Полный экран». Стоят по бокам от логотипа DEEIX в шапке сайта (marginTop 6dp, промежуток 84dp — подобрано по скриншоту 1080 px).
- Окно edge-to-edge (`setDecorFitsSystemWindows(false)`), отступы под вырез камеры и клавиатуру считаются вручную через insets.
- Pull-to-refresh сознательно не делали: мешает прокрутке чата.
- Иконка: `icon.png` в корне (`tools/make_icons.py` делает mipmap; без `icon.png` сборка берёт иконку с сайта через `tools/fetch_icon.py`).
- Подпись: фиксированный `app/debug.keystore`, чтобы новые APK ставились поверх старых. Только для личного использования — не менять ключ, иначе придётся удалять приложение.

## Сборка

- Android SDK в облачной среде агента обычно нет — собирает GitHub Actions (`.github/workflows/build.yml`, Gradle 8.7, Java 17).
- Push в `main` → сборка → APK публикуется в релиз `latest` (старый релиз удаляется) с именем `deeix-ru-<версия>.apk` и заголовком «DEEIX-RU <версия>».
- Пробная сборка ветки (без публикации релиза): `gh workflow run build.yml --ref <ветка>`, APK — в Artifacts запуска.

## Правила

1. **Версия.** При каждом изменении поднимать `versionName` в `app/build.gradle.kts` (сейчас `1.3`, следующая `1.4`) и называть версию Kimi в ответе. `versionCode` ставится сам из номера запуска.
2. **Слияние без согласия.** Kimi разрешил (2026-10-23): PR → пробная сборка ветки зелёная → сразу squash merge → дождаться релиза → прислать Kimi ссылку на APK с версией. Это правило только для tap-shell; в deeix-ru-build сливать только после «да» Kimi.
3. Общаться с Kimi по-русски и просто.
4. Репозиторий публичный: никаких ключей, паролей и личных данных.

## История

- 1.0 — первая версия; PR #1 — кнопка «Обновить».
- 1.1 (PR #2) — кнопки в ряд вверху по центру, версия в имени APK.
- 1.2 (PR #3) — edge-to-edge, убрана полоса над вырезом камеры в полном экране.
- 1.3 (PR #4) — кнопки по бокам от логотипа DEEIX.

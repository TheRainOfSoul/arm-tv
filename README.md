# ArmTV — CloudStream-расширение (films.bz, hayertv.com, armfilm.co)

Плагин для приложения **CloudStream 3** (Android TV): добавляет три армянских сайта
как источники. Каталог, поиск, карточки и извлечение видеопотока — свои; UI, плеер,
выбор качества, полный экран, управление пультом и «не засыпать при просмотре»
даёт само приложение CloudStream.

> Это **Фаза 1**. Своё приложение `HHTV` (Compose for TV) лежит рядом в `../app` и не трогается —
> к нему вернёмся в Фазе 2, когда захочешь свой бренд и UI.

## Структура

```
cs-extensions/
├── build.gradle.kts          # корень: плагин сборки CloudStream, версии, зависимости
├── settings.gradle.kts       # include("ArmTV")
├── gradle/wrapper/…          # Gradle 8.9 (совместим с плагином CloudStream)
└── ArmTV/
    ├── build.gradle.kts      # метаданные .cs3 (описание, язык, статус, иконка)
    └── src/main/kotlin/com/hhteam/armtv/
        ├── ArmTvPlugin.kt        # точка входа: регистрирует 3 провайдера
        ├── DleProvider.kt        # ⭐ вся общая логика DLE + сниффер потока
        ├── FilmsBzProvider.kt    # films.bz  (URL + ряды + селекторы)
        ├── HayerTvProvider.kt    # hayertv.com
        └── ArmFilmProvider.kt    # armfilm.co (/hy/)
```

Добавить новый сайт = ещё один наследник `DleProvider` + строка `registerMainAPI(...)` в `ArmTvPlugin`.

## ⚠️ Статус: скелет, нужен один проход «собрать и подогнать»

Честно: этот код **ещё не собирался и не запускался** (сборка требует Android SDK,
зависимостей CloudStream и сети — это делается у тебя в Android Studio). Две вещи нужно
подтвердить на живом окружении:

1. **CSS-селекторы карточек** (`cardSelector`, `titleInCardSelector` в каждом провайдере).
   Точную вёрстку по домашним страницам снять не удалось (часть подставляется JS).
   Селекторы заданы типовыми для DLE и помечены `TODO`. Проверка: открыть сайт в браузере →
   инспектор элементов → посмотреть, в каком блоке лежит карточка, и поправить строку.
2. **Версии сборки** (`build.gradle.kts`). Связка AGP 8.7.3 / Gradle 8.9 / плагин
   `recloudstream:gradle` рабочая, но плагин иногда обновляют. Если Gradle ругается —
   сверься с актуальным официальным шаблоном:
   <https://github.com/recloudstream/cloudstream-extensions> (его корневой `build.gradle.kts`).

Извлечение потока (`loadLinks`) от селекторов **не зависит** — оно универсально через WebView-сниффер.

## Сборка

**Android Studio:** `File → Open →` папка `cs-extensions` (открывать именно её, НЕ корень
`G:\AndroidTV`, иначе подтянется Gradle 9 от HHTV). Дождаться sync → `Build → Make Project`.

**Командная строка (PowerShell, из папки `cs-extensions`):**
```powershell
.\gradlew.bat make        # соберёт ArmTV/build/ArmTV.cs3
```
Плагин сборки CloudStream добавляет задачи `make` и `makePluginsJson`
(генерит `plugins.json`/`repo.json` для публикации).

## Установка и тест на Android TV

1. Поставить **CloudStream 3** на TV-бокс или Android-TV-эмулятор (APK с их GitHub-релизов).
2. **Быстрый тест без публикации:** положить `ArmTV.cs3` на устройство и открыть его в CloudStream
   (Settings → Extensions → локальная установка), либо — для итераций —
3. **Через репозиторий:** запушить `.cs3` + `plugins.json`/`repo.json` в свой GitHub,
   в приложении: Settings → Extensions → Add repository → raw-URL твоего `repo.json`.
   Дальше провайдеры `Films.bz`, `HayerTV`, `ArmFilm` появятся в списке источников.

Проверять начни с **Films.bz** (нет antibot, самый предсказуемый).

## Как закрыты твои требования (в этом маршруте)

| Требование | Где решается |
|---|---|
| Управление пультом (Leanback) | ✅ CloudStream (TV-режим) |
| Полный экран | ✅ плеер CloudStream |
| Смена качества | ✅ `M3u8Helper` разворачивает master.m3u8 в дорожки → меню качества в плеере |
| Не засыпать при просмотре / спать на паузе | ✅ плеер CloudStream (keep-screen-on по состоянию воспроизведения) |
| Без рекламы и попапов | ✅ структурно: в плеер уходит **только** ссылка на видео, страница сайта не показывается |
| Блокировка хостов (адблок) | 🟡 реклама и так не доходит. Системная DNS-блокировка (Pi-hole-стиль) — это уже Фаза 2 в HHTV, где WebView под нашим контролем. Затравка списка: `liveinternet.ru`, `oauth.vk.com`, `connect.ok.ru`, `oauth.yandex.ru`, `connect.mail.ru` |
| Anti-bot hayertv | ✅ `loadLinks` идёт через WebView, который исполняет JS и проходит antibot-куку |

## Дальше

- Подогнать селекторы (шаг 1 выше) → каталог/поиск заработают.
- Проверить `loadLinks` на реальном фильме. Если WebView-сниффер медленный —
  поймать в DevTools реальный запрос `.m3u8` и добавить **прямой** экстрактор (быстрее).
- Довести серии (`parseEpisodes`) под конкретную вёрстку плейлистов.

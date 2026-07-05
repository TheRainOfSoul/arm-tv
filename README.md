# builds — автогенерируемая ветка

GitHub Actions публикует сюда собранные артефакты. **Руками не редактировать.**

## Установка в CloudStream (Android TV)

Settings → Extensions → Add repository → вставить URL:

```
https://raw.githubusercontent.com/TheRainOfSoul/arm-tv/builds/repo.json
```

Внимание: добавляется **repo.json** (дескриптор репозитория), а не plugins.json.

После добавления в списке источников появятся `Films.bz`, `HayerTV`, `ArmFilm`.

| Файл | Назначение |
|------|-----------|
| `repo.json` | дескриптор репозитория — его URL добавляют в приложении |
| `plugins.json` | список плагинов (на него ссылается repo.json) |
| `ArmTV.cs3` | сам плагин |

Исходники и как это собрано — в ветке `main`.

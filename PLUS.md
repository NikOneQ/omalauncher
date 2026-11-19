# Omalauncher+

Форк [Omalauncher](https://github.com/SimonSchubert/omalauncher), который ставится рядом с ним как отдельное
приложение (`dev.omalauncher.plus`).

Что добавлено:

- **Перенос из Omalauncher в один клик** (настройки → «перенести всё из omalauncher», а также первый экран при
  установке): виджеты, главный экран, стиль, темы и весь Linux копируются через root (`storage/MoveFromOmalauncher.kt`).
  Ссылки proot (`--link2symlink`) с абсолютными путями переписываются на новую папку. Omalauncher сохраняет свою копию.
- **Общие обои**: фон лаунчера (в том числе темы из магазина) сам ставится обоями Android на главный экран и экран
  блокировки (`theme/SystemWallpaperSync.kt`, настройка `wallpaper.system` в style.toml, переключатель в «стиль»).

Сборка: GitHub Actions (`.github/workflows/plus.yml`) собирает APK, подписанный отладочным ключом; на телефоне он
переподписывается своим ключом, который никуда не загружается.

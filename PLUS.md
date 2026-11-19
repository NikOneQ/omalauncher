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
- **Свои анимации открытия приложений** (`anim/`): наборы ios, coloros, pixel, oneui (окно вырастает из иконки со
  скруглёнными углами, главный экран отъезжает назад), fade, slide, system и none; скорость и скругление. Всё в «стиль»
  → «анимации» или в style.toml (`animations.style`, `animations.speed`, `animations.corners`). Лаунчер сам рисует
  переход (remote transition), для этого нужно системное разрешение CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS: его даёт
  модуль KernelSU из `ksu-module/` (лаунчер становится priv-app). Без модуля — простое увеличение из точки нажатия.
  Скрытые классы Android берутся из заглушек `hidden-api-stubs/` при сборке и из системы при работе.

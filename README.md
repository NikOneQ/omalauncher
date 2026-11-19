# Omalauncher+

> **This is a fork.** Omalauncher+ is an unofficial fork of
> [Omalauncher](https://github.com/SimonSchubert/omalauncher) by Simon Schubert. It is not made, reviewed or
> supported by the original author, and it is not the app on Google Play. Everything below the line further down is
> the original project's README, kept as it is.

Omalauncher+ installs **next to** Omalauncher as an app of its own (`dev.omalauncher.plus`), so the original keeps
working until you're happy to switch. It keeps all of Omalauncher (themes, OWL widgets, the Linux system, coding
agents) and adds animations like a phone maker's own launcher has, a shared wallpaper, and a one-tap move of all your
data from Omalauncher.

## What's different from Omalauncher

| | Omalauncher | Omalauncher+ |
|---|---|---|
| Opening an app | no animation | the app grows out of its icon with rounded corners; home recedes, dims and blurs behind it |
| Animation styles | – | **ios**, **coloros**, **pixel**, **one ui** (from the icon), **fade**, **slide**, **system**, **none**; speed, blur and corner radius to taste |
| Closing an app to home | Android's / One UI's | flies back into its icon in the same style, for every app, including ones that handle back themselves |
| Back gesture | Android's | the app's window shrinks under your finger in **every** app (card, slide, subtle or follow), home's blurred background behind it; it flies on into the icon if the app closes and springs back if it doesn't |
| Coming back home | – | home rises back from blurred and receded in the chosen style |
| Wallpaper | the launcher's own | Android's wallpaper (home and lock screen) follows the launcher's background, store themes included |
| Moving from Omalauncher | – | one tap (settings, or the first screen): widgets, home, style, themes and the whole Linux are copied over with root |
| Settings | – | an **animations** page: styles with descriptions, the back gesture, and restarts of System UI, the launcher, and a soft reboot (a zygote restart, not ksud) |

Everything else works as in Omalauncher, and this fork follows its code.

## What it needs

- **Root** (KernelSU or Magisk) for moving your data in and for the restarts. Temporary root is fine.
- **LSPosed or Vector** with the **Omalauncher+ system bridge** module (`vector-module/`) for the full animations.
  Its scope is *System Framework* (it answers one permission check, `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`, for
  this app only) and *System UI* (it hands the launcher the back gesture and the closing animation, as SystemUI
  hands them to a phone's own launcher). Nothing is mounted into `/system`, so a reboot without root simply turns
  it off.
- Without the module, the launcher still works and falls back to Android's simple scale-up animation.

The animations reach into Android's private window APIs (remote transitions, WMShell's back animation and transition
filters). They were made and tested on a **Samsung Galaxy Z Fold6 with One UI 9 / Android 17**; on other phones and
versions parts of them may do nothing, and the launcher falls back.

## Getting it

There are no releases. GitHub Actions (`.github/workflows/plus.yml`) builds the APK on every push and keeps it as the
`omalauncher-plus-apk` artifact, signed with a debug key: sign it with a key of your own (`apksigner sign`) and keep
using that key, or Android won't install the next build over it. The module builds with `vector-module/build.sh`.

To switch: install Omalauncher+, open it, tap **move everything from omalauncher**, allow root, wait for it to open
again, and make it the default home app. Omalauncher keeps its own copy until you remove it.

## Where the changes are

- `app/src/main/kotlin/dev/omalauncher/anim/`: the animations (opening, closing, back to home, home's return)
- `storage/MoveFromOmalauncher.kt`: the move, which also points proot's hard-link symlinks at the new place
- `theme/SystemWallpaperSync.kt`: the shared wallpaper
- `ui/settings/AnimationsScreen.kt`: the animations page
- `hidden-api-stubs/`: compile-time stand-ins for Android's hidden window classes
- `vector-module/`: the LSPosed/Vector module
- `ksu-module/`: an older approach that mounts the app as a system app; not recommended with temporary root

## License and credits

MIT, like Omalauncher: © 2026 Simon Schubert and contributors, see [LICENSE](LICENSE). The changes in this fork are
under the same license. All credit for the launcher itself goes to the original project.

### Коротко по-русски

**Omalauncher+ — неофициальный форк** [Omalauncher](https://github.com/SimonSchubert/omalauncher). Ставится рядом с
оригиналом отдельным приложением. Добавляет анимации как у лаунчеров производителей (наборы в стиле iOS, ColorOS,
Pixel, One UI и другие), окно за пальцем при жесте «назад» во всех приложениях, закрытие приложений в иконку, общие
обои с Android и перенос всех данных из Omalauncher в один клик. Для полных анимаций нужен root и модуль для
LSPosed/Vector из `vector-module/`; без него лаунчер работает с простыми анимациями. Проверено на Galaxy Z Fold6
(One UI 9, Android 17).

---

*The original Omalauncher README follows. Its Google Play link is the original app, not this fork.*

## Omalauncher (original)

An Android home screen with the look of [Omarchy](https://omarchy.org), and an
optional Arch Linux ARM system next to your phone apps. Omalauncher runs the
system under proot, without root, and is its Wayland compositor: every Linux
window opens as its own Android task, with its own card in Recents.

<a href="https://play.google.com/store/apps/details?id=dev.omalauncher"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"></a>

![The home screen in twelve Omarchy themes, each with different widgets](art/collage.png)

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="24%" alt="Screenshot">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="24%" alt="Screenshot">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="24%" alt="Screenshot">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="24%" alt="Screenshot">
</p>

- **Themes**: all 22 Omarchy themes, with their wallpapers, applied to the
  launcher and to the Linux apps, plus the community themes from
  [omarchy.org/themes](https://omarchy.org/themes/), or your own in
  `~/.config/omarchy/themes`
- **Widgets** written in OWL, a small declarative language you can edit on the
  phone, plus a [widget store](https://github.com/SimonSchubert/omarchy-widgets)
- **Linux, optional**: pacman and the AUR, Linux apps in their own windows,
  terminal widgets, a file manager, and opt-in GPU acceleration on Mali and Adreno 6xx+ GPUs
- **Coding agents**: the bar at the bottom sends a prompt to Claude Code, Codex,
  Copilot, opencode and others, which open in a terminal
- No account, no ads, no tracking

Needs an arm64 phone with Android 10 or newer. Without Linux, the launcher still
has the themes, wallpapers, Android apps and the widgets that don't run commands.

### Using it

The bar: the one input at the bottom of the home screen opens apps and asks
coding agents. Focusing it opens the list of Linux and Android apps; typing
narrows the list, and Enter opens the highlighted app or sends the text to the
agent. Short queries that name an app open it, sentences go to the agent; Tab
and the arrow keys move the highlight. Tap the agent's name to pick another
installed one; the choice is saved in `~/.config/omalauncher/agent`. Hold the mic to talk and release to send, or tap it to dictate
into the field. Speech is recognized by Android (on the device when the phone
supports it), so only text reaches Linux.

The app drawer is a widget too, `drawer.owl` in the widgets folder, so an
agent can rewrite it. Here Grok sorts it into folders:

<img src="art/drawer-folders.webp" width="360" alt="Grok rewrites drawer.owl, and the app drawer opens sorted into folders">

### The `omalauncher` command

Scripts and agents in Linux reach the phone through `omalauncher`:

```sh
omalauncher location          # approximate {lat, lon, accuracy, time}
omalauncher calendar 7        # the events of the next 7 days (14 if not given)
omalauncher battery           # level, charging, temperature, health...
omalauncher airpods anc       # the connected AirPods: read them, or change a mode or setting
omalauncher notify "build done" "all tests passed"
omalauncher share report.pdf  # Android's share sheet, for you to pick an app
omalauncher folders           # the phone folders you let Linux reach
omalauncher get Camera/IMG_1.jpg .          # copy from one into Linux
omalauncher put report.pdf Documents        # and back
omalauncher pick              # Android's file picker; what you pick is copied to ~/Downloads
omalauncher version           # the launcher's version and the commands it has
```

It prints JSON and only uses what the launcher was already allowed: it never
asks for a permission itself. Exit codes: 1 nothing to say (no location fix
yet, no AirPods connected), 2 wrong arguments, 3 the launcher isn't allowed
it, 4 the launcher isn't running or didn't answer. `omalauncher --help` has the details. The
coding agents are told about it, so you can ask one to tell you when a long
task is done. `phone`, its name before 0.20, still works.

### Phone files

The launcher asks for no storage permission. Linux has `/sdcard`, but Android
shows it only the files Linux saved there itself: what it puts in
`/sdcard/Download` or `/sdcard/Documents` (pictures in `/sdcard/Pictures`), your
other apps see too (Android 11 and newer). For your own photos, downloads and
documents, add folders at the top of settings, permissions, in Android's folder
picker. Files shows them under "phone folders", where you can open, copy, move,
rename and delete files and copy them to and from Linux; agents reach them
through the `omalauncher` commands above. Android keeps the access until you
remove the folder there.

Files also get into Linux from Android's share sheet: while Linux is installed,
other apps' share sheets list **Linux**, and what you share there is saved in
Linux's `~/Downloads`, where `omalauncher pick` saves what you pick. A link or
text becomes a small `shared-<date>-<time>.txt` file.

Touch input in Linux windows: tap is a left click, long press is a right
click, drag scrolls; apps with touch support (foot, GTK) get real touch.

### Keyboard and gamepad

| Keys | Where | What it does |
| --- | --- | --- |
| Super+D | home | the app drawer |
| Super+T | home | a terminal |
| Super+Down | home | the swipe-down panel |
| Super+1..9 | home | home pages |
| Super+W | Linux window | close it |
| Super+Esc, gamepad B | Linux window, terminal tile | leave it without closing it, since Esc itself is theirs (the gamepad's buttons stay the launcher's) |
| Arrows, Tab, D-pad | home, drawer, panels, widgets | move between tiles and what takes taps, past the first or last tile to the next page |
| Right / Left | widget | into its own buttons / back out to its tile |
| Up | the bar | back to the tiles, or into the open drawer |
| Enter (A) | tile | open it; on a terminal tile, go into it, then type into it or restart it once it has exited |
| Menu, or hold Enter | tile | long-press it, which selects a home tile for editing |
| Esc (B) | drawer, panel, widget screen | close it, like Back |
| Arrows, Page Up/Down | long text (a license, a file being shared) | scroll until it has all been shown, a screenful at a time with Page Up/Down |
| Enter, then arrows | pages screen | pick a page up by its ≡ and move it, until Enter or Esc puts it down |
| Menu | notification in the swipe-down panel | dismiss it |

Android keeps Super on its own, Super+Space, Super+A and Super+Enter for
itself, so Omarchy's Super+Space and Super+Enter can't be used.

### Building

```sh
./scripts/fetch-proot.sh          # Termux's proot -> app/src/main/jniLibs (needs bsdtar)
./gradlew :app:installFossDebug
# optional: skip the 830 MB download on the device
adb push ArchLinuxARM-aarch64-latest.tar.gz /sdcard/Android/data/dev.omalauncher/files/
```

Tests: `./gradlew :owl:test :app:testFossDebugUnitTest`. `./gradlew :owl:owlJar`
builds `owl/build/cli/owl.jar`, the checker the widget store's CI runs.

In debug builds you can launch guest commands with
`adb shell am start -n dev.omalauncher/.MainActivity --es exec foot`.

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): the compositor, rendering, GPU
  acceleration, and how updates reach existing installs
- [docs/RELEASING.md](docs/RELEASING.md): Play releases and store assets
- [app/src/main/assets/widgets/README.md](app/src/main/assets/widgets/README.md):
  the widget language
- [AGENTS.md](AGENTS.md): working on the code, as a person or a coding agent:
  build and test, the rules for changes, commit style

### Layout

- `app/`: the Android app (Kotlin, Jetpack Compose, and the JNI bridge in `src/main/cpp`)
- `owl/`: the widget language and the `owl` command line, plain Kotlin
- `art/`: SVG masters for the app, agent and store icons
- `store/`: Play listing sources (screenshot frames, privacy policy)
- `fastlane/`: the Play listing and the upload lanes
- `scripts/`: fetching proot and theme colors, rendering icons, screenshots and widget previews

### License

MIT, see [LICENSE](LICENSE), for Omalauncher's own code and artwork. The
coding agents' logos, the symbol font and Omarchy's theme colors are not
covered; [THIRD_PARTY.md](THIRD_PARTY.md) lists them with their sources and
licenses. The app also ships proot, talloc and libandroid-shmem (GPL-2.0,
LGPL-3.0 and BSD-3-Clause, prebuilt by Termux), listed in the app under
settings › licenses.

Omalauncher is an independent project and is not affiliated with or endorsed
by Omarchy, Arch Linux, or the makers of the coding agents it installs.

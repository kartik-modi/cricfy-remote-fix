# CRICFy Remote Fix

A tiny Android TV / Fire TV helper that makes **CRICFy TV** usable with a normal TV remote.
App link : https://cricify.org/download3/

CRICFy TV has two screens a remote cannot use:

| Problem | What this helper does |
|---|---|
| **"Multiple Links Available"** dialog: Up/Down move an invisible selection, OK closes the dialog instead of playing. | Puts the remote's focus on the stream list and draws an orange outline on the selected stream (or Cancel). Up/Down move it, OK plays it. |
| **Full-screen button** in the player cannot be reached with the remote. | Presses it once, automatically, when a stream starts. |

It does not modify CRICFy TV, and it does not contain or download it. You install CRICFy TV yourself.

- About 10 KB, Java only, no dependencies.
- **No permissions at all**: no internet, no storage. It runs as an accessibility service limited to
  CRICFy TV's windows (`packageNames="app.cricfy.tv"`), so it never sees any other app's screen.
- Android 8.0 (API 26) or newer: Android TV, Google TV, Fire TV (Fire OS 7/8).

## Samsung (Tizen) and LG (webOS) TVs

**This cannot run on Tizen or webOS.** Those TVs do not run Android apps, so neither CRICFy TV nor
this helper can be installed on them, and there is nothing in this repo to port: the helper works by
reading CRICFy TV's Android screens.

To use it with a Samsung TV, plug an Android-based streaming stick into the TV's HDMI port and
install everything on the stick:

- Amazon **Fire TV Stick** (4K, 4K Max, HD), or
- **Google TV Streamer** / Chromecast with Google TV, or any Android TV box.

Then follow the steps below on the stick.

## Install (you, or your AI agent)

These steps are written so an agent such as Claude Code can follow them on your computer. You need
JDK 17, the Android SDK (platform 34, build-tools 34) and `adb`.

### 1. Turn on debugging on the stick

- **Fire TV:** Settings → My Fire TV → About → click the device name 7 times to unlock Developer
  options. Then Settings → My Fire TV → Developer options → **ADB debugging: ON**.
- **Google TV / Android TV:** Settings → System → About → click **Android TV OS build** 7 times. Then
  Settings → System → Developer options → **USB debugging: ON** (and Network debugging if offered).

Find the stick's IP address (Settings → Network) and keep the TV on the screen: the first connection
shows an "Allow USB debugging?" prompt that must be accepted with the remote.

### 2. Install CRICFy TV

Install CRICFy TV on the stick the way you normally would. The helper only does something while
CRICFy TV (package `app.cricfy.tv`) is open.

### 3. Build and install the helper

```bash
git clone https://github.com/kartik-modi/cricfy-remote-fix
cd cricfy-remote-fix
./gradlew --no-daemon assembleRelease
adb connect <stick ip>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 4. Turn the helper on

Fire TV hides third-party accessibility services from its Settings, so enable it over adb (this works on
every Android TV). If another accessibility service is already on, keep it: read the current value first
and append ours with a `:` separator.

```bash
adb shell settings get secure enabled_accessibility_services
# empty or "null":
adb shell settings put secure enabled_accessibility_services dev.kartikmodi.cricfyremote/.RemoteFixService
# otherwise:   ...put secure enabled_accessibility_services <existing>:dev.kartikmodi.cricfyremote/.RemoteFixService
adb shell settings put secure accessibility_enabled 1
```

Check it is running:

```bash
adb shell dumpsys accessibility | grep -A1 "Bound services"
```

It should list `CRICFy Remote Fix`.

### 5. Try it

Open CRICFy TV, pick a match. The links dialog should open with an orange outline on the first stream.
Move with Up/Down, press OK, and the player should switch to full screen by itself.

**Test with the real remote.** Keys sent with `adb shell input keyevent` skip accessibility services, so
an agent's injected key presses do not prove the dialog fix works (screenshots of the outline do).

### 6. Close debugging afterwards

ADB over the network is unencrypted. When done, turn ADB debugging / USB debugging back off in Developer
options.

## Undo

```bash
adb shell settings put secure enabled_accessibility_services <the value from step 4, without ours>
adb uninstall dev.kartikmodi.cricfyremote
```

## If CRICFy TV updates and the fix stops working

The helper finds CRICFy TV's views by their IDs (`link_listView`, `link_cancel`, `dialog_link_root`,
`dialog_link_card`, `playerLayout`, `player_view`, `exo_fullscreen_btn`) in
`app/src/main/java/dev/kartikmodi/cricfyremote/CricfyRemote.java`. If an update renames them, dump the
screen with `adb shell uiautomator dump` while the dialog or player is open, and update the IDs there.

## License

MIT. Not affiliated with CRICFy TV.

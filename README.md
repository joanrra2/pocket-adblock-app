# Pocket AdBlock (phone app)

[![build](https://github.com/joanrra2/pocket-adblock-app/actions/workflows/build.yml/badge.svg)](https://github.com/joanrra2/pocket-adblock-app/actions/workflows/build.yml)

Android and iPhone app for [Pocket AdBlock](https://github.com/joanrra2/pocket-adblock)
boards: finds them on your Wi-Fi without knowing their address, remembers the login
and opens their settings. Home network only; no remote access, no analytics.

Status: **Android: M1 done** (tested on a real board). **iPhone: M2 in progress**
(builds in CI; not yet run on a device).

## What it does

- **Finds boards** by mDNS (`_http._tcp`, instance "DNS Sinkhole") and, when the router
  drops mDNS, by trying every address of the phone's /24 (64 at a time, ~4 s). A board
  is recognised by its `401` answer with `realm="sinkhole"`; no password is sent.
- **Remembers them**: the list of boards is kept in the app's settings; each password
  is encrypted (Android Keystore, AES-GCM / iOS Keychain, this device only). A board
  still on its factory password is not stored. Optional fingerprint, face or screen
  lock before opening or pausing a board.
- **Board list** with live status every 10 s: blocked share, queries, list size,
  firmware update, paused state; **Pause** 5 / 30 / 60 min or until resumed.
- **Opens the board's own page** logged in: the web view answers the board's
  Basic-auth challenge with the saved login. Links that leave the board open in the
  browser.
- **Finds a board again** when DHCP gives it a new address (matched by hostname).
- New board on its setup Wi-Fi (phone at 192.168.4.x): the board at 192.168.4.1 is
  marked and opened with a setup hint.
- Typed addresses (`192.168.1.50`, `sinkhole.local`, `host:port`), local ones only.
- **Sets up [Pocket Wake](https://github.com/joanrra2/pocket-wake)** (remote Wake-on-LAN
  over MQTT): a board's menu → *Set up Pocket Wake* reads its broker settings and hands
  them to the Pocket Wake app with the same setup link as the board's QR code (never a
  password). On iPhone, and without the Android app, Pocket Wake's web app opens.

## Build

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).

```bash
./gradlew :shared:jvmTest              # shared unit tests (JVM)
./gradlew :androidApp:assembleDebug    # debug APK
./gradlew :androidApp:installDebug     # install on a phone over USB
```

iPhone (macOS with Xcode):

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open PocketAdBlock.xcodeproj
```

The Xcode build compiles the shared Kotlin code itself (`embedAndSignAppleFrameworkForXcode`).
CI builds both apps on every push; on Windows the iOS targets are skipped.

Check against a real board on your network (sends no password):

```bash
POCKETADBLOCK_IT=1 POCKETADBLOCK_BOARD=192.168.18.23 ./gradlew :shared:jvmTest --tests '*RealBoardCheck*'
```

End-to-end test on an Android emulator (GitHub Actions, `.github/workflows/e2e.yml`):
both apps are installed and `tools/e2e_android.sh` finds the fake board, logs in, saves
it and checks that Pocket Wake opens with the broker filled in. Screenshots are kept as
an artifact. The same script runs against a phone over adb.

`tools/fake_board.py` is a fake board (test login `admin` / `test-password-123`) for
trying the login, the web view and pause without a real board:
`python tools/fake_board.py`, `adb reverse tcp:8080 tcp:8080`, then type `127.0.0.1:8080`.

## Layout

```
shared/       Kotlin Multiplatform + Compose Multiplatform
  commonMain  discovery, board client, saved boards, screens, ES/EN text
  androidMain NsdManager mDNS, Keystore, BiometricPrompt, WebView with auto-login
  iosMain     Bonjour, getifaddrs, Keychain, Face ID, WKWebView with auto-login
  jvmMain     PC runner for tests
androidApp/   the Android app (MainActivity, manifest, icon)
iosApp/       the iPhone app (XcodeGen project.yml, SwiftUI entry point)
tools/        fake board for testing
```

## Privacy

The app talks only to boards on your network. No analytics, no crash reporting, no
accounts. Plain HTTP is used only with private, link-local and `.local` addresses,
the same trust model as the board's web page.

## License

MIT, see [LICENSE](LICENSE).

# Pocket AdBlock (phone app)

Android and iPhone app for [Pocket AdBlock](https://github.com/joanrra2/pocket-adblock)
boards: finds them on your Wi-Fi without knowing their address, remembers the login
and opens their settings. Home network only; no remote access, no analytics.

Status: **milestone M0** (proof of concept). Android only for now; the iPhone parts
arrive in M2.

## What works in M0

- Finds boards by mDNS (`_http._tcp`, instance "DNS Sinkhole") and, when the router
  drops mDNS, by trying every address of the phone's /24 (64 at a time, ~4 s). A board
  is recognised by its `401` answer with `realm="sinkhole"`; no password is sent.
- Marks a new board's setup Wi-Fi (phone at 192.168.4.x, board at 192.168.4.1).
- Typed addresses (`192.168.1.50`, `sinkhole.local`, `host:port`), local addresses only.
- Login check with `GET /api/status`, then the board's own web page in a WebView that
  answers the board's Basic-auth challenge with that login.

Credentials live only in memory in M0; secure storage (Keystore / Keychain) comes in M1.

## Build

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).

```bash
./gradlew :shared:jvmTest              # shared unit tests (JVM)
./gradlew :androidApp:assembleDebug    # debug APK
./gradlew :androidApp:installDebug     # install on a phone over USB
```

Check against a real board on your network (sends no password):

```bash
POCKETADBLOCK_IT=1 POCKETADBLOCK_BOARD=192.168.18.23 ./gradlew :shared:jvmTest --tests '*RealBoardCheck*'
```

iOS targets compile only on macOS (CI); on Windows they are skipped.

## Layout

```
shared/       Kotlin Multiplatform + Compose Multiplatform
  commonMain  discovery, board client, screens, ES/EN text
  androidMain NsdManager mDNS, WebView with auto-login
  iosMain     placeholders until M2
  jvmMain     PC runner for tests
androidApp/   the Android app (MainActivity, manifest, icon)
```

## License

MIT, see [LICENSE](LICENSE).

Finds your [Pocket AdBlock](https://github.com/joanrra2/pocket-adblock) boards on your Wi-Fi,
remembers their login and opens their settings. Home network only; no analytics.

### Android

Download `pocket-adblock-{VERSION}.apk` on the phone and open it (allow installing from
your browser or file manager when asked). Updates install over the previous version.

### iPhone

There is no App Store version. `pocket-adblock-{VERSION}-unsigned.ipa` is unsigned: you
sign it with your own Apple ID using a sideloading tool on a computer, for example
[Sideloadly](https://sideloadly.io) or [AltStore](https://altstore.io):

1. Install the tool, connect the iPhone by USB and open the `.ipa` with it.
2. Sign in with your Apple ID (a free one works).
3. On the iPhone: Settings → General → VPN & Device Management → trust your Apple ID.
   On iOS 16 and later also turn on Settings → Privacy & Security → Developer Mode.
4. Open the app and allow **Local Network** access (needed to find boards).

With a free Apple ID the app must be signed again every 7 days (AltStore can do it
automatically) and up to 3 such apps can be installed at a time.

`SHA256SUMS.txt` has the checksums of both files.

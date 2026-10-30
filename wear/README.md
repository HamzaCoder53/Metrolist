# Wavelet

A standalone YouTube Music client for Wear OS watches. It runs without a phone and is
built for the TicWatch Pro 5 Enduro, but any Wear OS 3+ watch (API 30+) should work.

## Credits

Wavelet is built on [Metrolist](https://github.com/MetrolistGroup/Metrolist) by the
MetrolistGroup contributors. It lives in a fork of the Metrolist repository and reuses its
`:innertube` and `:streaming` modules, and through them the
[InnerTubeX](https://github.com/MetrolistGroup/innertubex) library. Streaming fixes from
Metrolist reach Wavelet by merging upstream.

## Disclaimer

Wavelet is not affiliated with, endorsed by or sponsored by YouTube or Google. "YouTube"
and "YouTube Music" are trademarks of Google LLC.

## License

GPL-3.0, the same license as Metrolist. See [LICENSE](../LICENSE).

## Features (Phase 1)

- Search by voice or keyboard; results scroll with the crown.
- Now Playing: play/pause, skip, ±10 s seek and artwork. The crown changes the volume.
- Background playback with a media notification and a Wear OS Ongoing Activity.
- Plays only through Bluetooth headphones. Without them, Wavelet opens the Bluetooth
  picker and starts playing once headphones connect.
- Low audio quality and small artwork by default, to save battery and data.

## Features (Phase 2: offline)

- Search albums and playlists as well as songs (the three filter icons under the search
  buttons).
- Downloading:
  - **Download all** on an album or playlist page.
  - The download icon on Now Playing.
  - Long-press a song in search results.
- **Downloads** library:
  - Shows storage used and free space.
  - Albums and playlists are grouped.
  - **Play all**; tap a song to play it.
  - Long-press a song, or use **Delete all**, to delete (asks to confirm).
- Settings → Downloads:
  - **Wi-Fi only** (on by default). Downloads wait while the watch is on mobile data.
  - **Only while charging**.
  - Downloads pause and resume on their own.
- Downloaded songs play with no connection at all, including their artwork.
- **Listening cache** (like Metrolist's player cache):
  - Songs you stream are saved while they play, so you can replay them offline without
    downloading. They're listed under **Recently played (offline)** in Downloads.
  - Set the size in Settings → Listening cache → **Cache size**. Drag the slider, tap −/+,
    or turn the crown: Off, 100 MB, 250 MB, 500 MB (default), 1 GB, 2 GB, 4 GB, 8 GB,
    Unlimited.
  - The least recently played songs are removed first.
  - Even on Unlimited, at least 500 MB of the watch's storage is always left free.
  - **Clear cache** is just below the size row in Settings.
  - Downloads are kept separately and never removed by the cache.

## Features (Phase 3: account and extras)

- **Account** (Home → Account) has two ways to sign in:
  - **Sign in on watch:** Google's sign-in page opens on the watch, like Metrolist's
    login. It gives you your full YouTube Music account. It's only shown if the watch has a
    WebView; many Wear OS watches, including the emulator, don't.
  - **Sign in with a code:** the watch shows a short code. You enter it at
    google.com/device on any phone or computer. This uses Google's official device sign-in
    and the read-only YouTube Data API, and syncs liked songs and playlists. It needs an
    OAuth client in the build (see below).
- **Your library:** your liked songs and playlists, with **Play**, **Download liked songs**
  and **Sync now**. The last sync is saved, so the list still shows offline.
- **Synced lyrics:** tap the lyrics button on Now Playing. The current line replaces the
  title, with the next line dimmed underneath, and follows playback. Lyrics come from LrcLib
  through Metrolist's shared `:lyrics` module. Turn the button off in
  Settings → Lyrics.
- **Tile:** shows what's playing with play/pause, plus your two most recent songs (tap one to
  play it).
- **Complication:** shows the current song on any watch face that supports text
  complications. Tap it to open Wavelet.

## Features (Phase 4: watch extras)

- **Quick picks on Home:** suggestions from YouTube Music's home page. They're personalised
  when you're signed in on the watch, and hidden while offline.
- **Autoplay:** when the last song in your queue starts, Wavelet adds about 15 similar songs
  (YouTube Music's "up next" for that song). Turn it off in Settings → Playback.
- **Volume normalization:** turns loud songs down to YouTube's reference level, using each
  song's loudness value. That value is saved, so it also works for cached and downloaded
  songs offline. Quiet songs aren't boosted. Turn it off in Settings → Playback.
- **Skip silence:** off by default; turn it on in Settings → Playback.
- **Sleep timer** (Home → Sleep timer): 15, 30, 45, 60 or 90 minutes, or the end of the
  current song. The time left shows on the Home row.

### Setting up "Sign in with a code"

1. In Google Cloud Console, open a project and enable the **YouTube Data API v3**.
2. Configure the OAuth consent screen: external, in testing, with your Google account as
   a test user.
3. Create an OAuth client of type **TVs and Limited Input devices**.
4. Save its ID and secret outside the repo, in `~/.android/wavelet-oauth.properties`:

   ```properties
   clientId=YOUR_CLIENT_ID.apps.googleusercontent.com
   clientSecret=YOUR_CLIENT_SECRET
   ```

5. Rebuild. Without this file, the option shows "Not set up in this build".

## Build

```sh
./gradlew :wear:assembleDebug
# APK: wear/build/outputs/apk/debug/wear-debug.apk
```

## Publish to Play internal testing

Wavelet is on Google Play's **Wear OS internal testing** track (`wear:internal`) for its
owner's testing only; it is not published publicly.

```sh
wear/tools/publish.sh            # bump versionCode if needed, build, upload, show what's live
wear/tools/publish.sh --status   # read-only: what's on each track
```

- **Release notes:** edit `wear/src/main/play/release-notes/en-US/default.txt` before
  publishing.
- **Play service-account key:** `$WAVELET_PLAY_CREDENTIALS`, else
  `~/.tumbler/play-service-account.json`. The key is shared with Tumbler and stays outside
  the repo.
- **Upload key:** `~/.android/wavelet-upload.properties`. Back up both it and the `.jks` it
  points to.

## Install over Wi-Fi ADB

1. On the watch, turn on Developer options: Settings → System → About → tap
   *Build number* seven times.
2. In Settings → Developer options, turn on **ADB debugging** and **Debug over Wi-Fi**.
   Keep the watch on the same Wi-Fi network as your computer.
3. Open **Wireless debugging** → **Pair new device**. Note the pairing IP:port and
   code. Then on your computer:

   ```sh
   adb pair <watch-ip>:<pairing-port>     # enter the 6-digit code
   adb connect <watch-ip>:<debug-port>    # the port shown on the Wireless debugging screen
   adb -s <watch-ip>:<debug-port> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

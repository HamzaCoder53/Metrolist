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

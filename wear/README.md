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
- Plays through Bluetooth headphones by default. Pressing play without them shows a
  "Connect headphones" prompt with **Open Bluetooth** and a **Play on watch speaker** switch;
  playback starts once headphones connect or the switch is turned on. The same switch is in
  Settings → Playback.
- Low audio quality and small artwork by default, to save battery and data.

## Features (Phase 2: offline)

- Search albums and playlists as well as songs (the three filter icons under the search
  buttons).
- Downloading:
  - **Download all** on an album or playlist page. Playlists load up to 200 songs.
  - **Keep playlists in sync** (Settings → Downloads, on by default): downloaded playlists and
    downloaded liked songs follow YouTube Music. Songs added there are downloaded, songs removed
    there are deleted, and failed downloads are retried. It runs once a day when Wavelet starts
    and after every library sync; a failed or empty fetch never deletes anything. Albums aren't
    checked.
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

- **Sign in on phone** (recommended): tap it on the watch and the **Wavelet phone app** opens
  on your paired phone. Sign in to Google there, the way Metrolist does, and the phone sends the
  YouTube Music session to the watch over the Wear Data Layer. That link only connects your
  paired devices running Wavelet with the same signature. This gives you the full account on any
  watch, with no WebView needed and no 7-day expiry. The phone deletes its copy of the login
  afterwards.
  - The phone app and watch app must come from the **same place**: both from Google Play, or
    both from the same GitHub release. Play re-signs apps, and the Data Layer only connects apps
    with matching signatures.
- **Sign-in expired:** if a saved sign-in stops working, Account and Your library show
  "Your sign-in expired" with a **Sign in again** button. Your last synced library stays visible.
- **Sign in on watch** (Home → Account): Google's sign-in page opens on the watch, like
  Metrolist's login. It gives you your full YouTube Music account. It's only shown if the watch
  has a WebView; many Wear OS watches, including the emulator, don't.
- **Your library:** your liked songs and playlists, with **Play**, **Download liked songs**
  and **Sync now**. The last sync is saved, so the list still shows offline.
- **Synced lyrics:** tap the lyrics button on Now Playing. The current line replaces the
  title, with the next line dimmed underneath, and follows playback. Lyrics come from LrcLib
  through Metrolist's shared `:lyrics` module. Turn the button off in
  Settings → Lyrics.
- **Tile:** shows what's playing with play/pause, plus your two most recent songs (tap one to
  play it).
- **Complication ("Wavelet play/pause"):** add it to a watch face slot to play and pause from
  the watch face. Icon slots show a play or pause button; text slots also show the current
  song. Tapping it pauses or resumes without opening Wavelet. When nothing is queued, it opens
  Wavelet and plays your most recent song. Without headphones, it opens the "Connect
  headphones" prompt unless **Play on watch speaker** is on. To add it, long-press the watch
  face, tap **Edit**, pick a slot and choose Wavelet.

## Features (Phase 4: watch extras)

- **Recommended music on Home:** up to five of YouTube Music's home sections, such as Quick
  picks, mixes, and recommended albums and playlists. Songs play from their section; albums and
  playlists open their page. They're personalised with a full-account sign-in, and hidden while
  offline.
- **Playlists** (Home → Playlists): your Liked songs and playlists. The **Show uploaded music**
  switch adds the music you've uploaded to YouTube Music, grouped into albums the way YouTube
  Music groups them (from your files' album tags). Each album opens with **Play all** and
  **Download all**; **All songs** shows the full list.
- **Autoplay:** when the last song in your queue starts, Wavelet adds about 15 similar songs
  (YouTube Music's "up next" for that song). Turn it off in Settings → Playback.
- **Volume normalization:** turns loud songs down to YouTube's reference level, using each
  song's loudness value. That value is saved, so it also works for cached and downloaded
  songs offline. Quiet songs aren't boosted. Turn it off in Settings → Playback.
- **Skip silence:** off by default; turn it on in Settings → Playback.
- **Sleep timer** (Home → Sleep timer): 15, 30, 45, 60 or 90 minutes, or the end of the
  current song. The time left shows on the Home row.

## Build

```sh
./gradlew :wear:assembleDebug
# APK: wear/build/outputs/apk/debug/wear-debug.apk
```

## Download from GitHub

Every release on https://github.com/HamzaCoder53/Metrolist/releases has two APKs:

- `Wavelet-watch.apk`: install it on the watch over Wi-Fi ADB (see below).
- `Wavelet-phone.apk`: install it on the phone you've paired with the watch. It's only
  needed for **Sign in on phone**.

GitHub and Google Play builds are the same app with the same features.

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

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

## Build

```sh
./gradlew :wear:assembleDebug
# APK: wear/build/outputs/apk/debug/wear-debug.apk
```

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

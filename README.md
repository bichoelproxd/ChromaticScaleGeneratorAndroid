# Chromatic Scale Generator (Android)

Generates a chromatic scale from numbered samples (`1.wav`, `2.wav`, …), like the PC version
(`chromatic_gen.py`, which used `wxPython` and `Parselmouth`), but as an Android app.

**Languages:** English, Español, Português (Portugal) and Português (Brasil). The app asks for the language the
first time it opens, and the **Language** button at the top lets you change it at any time.

## How the port works

| PC | Android |
| --- | --- |
| `wxPython` (`app_ui.py`) | Native Kotlin UI (`MainActivity.kt`, `res/layout/activity_main.xml`) |
| `Parselmouth` (Python + Praat) | The **same Praat source code Parselmouth uses**, compiled with the NDK (C++) and called through JNI |
| `parselmouth.praat.call(...)` | A Praat script with those same commands: `app/src/main/assets/chromatic_scale.praat` |
| `DirPickerCtrl` | Android's folder picker (no storage permissions needed) |

Android has no Python and cannot run Parselmouth as is, so `scripts/fetch_praat.sh` downloads Parselmouth's source
code (which includes Praat 6.4.16 and `fmt`, pinned to fixed commits) and `app/src/main/cpp/CMakeLists.txt` builds
Praat for Android reusing Parselmouth's own `CMakeLists` (no GUI, audio or network).
`praat_core.cpp` does what `parselmouth.praat.run` does: it initializes Praat and runs a script, capturing its output.

The app copies the samples from the chosen folder into its private storage, processes them with Praat, and copies
`chromatic.wav` and `pitched_samples/` back to that same folder.

## Building the APK

GitHub Actions does it (`.github/workflows/build.yml`): on every push to `main`/`master`, on every pull request, or
manually from **Actions → Build APK → Run workflow**. It produces three APKs in the `ChromaticScaleGenerator-apk`
artifact:

- `app-universal-release.apk`: `armeabi-v7a` + `arm64-v8a` (works on any phone)
- `app-arm64-v8a-release.apk`: 64-bit only
- `app-armeabi-v7a-release.apk`: 32-bit only

Pushing a `v*` tag (for example `v1.0`) also publishes a Release with the APKs.
The first build takes a while (it compiles all of Praat); later ones use `ccache`.

Locally: `scripts/fetch_praat.sh && ./gradlew assembleRelease` (needs JDK 17, the Android SDK and NDK 27.2.12479018).
The APKs are signed with the debug key, so they can be installed directly (they are not suitable for Google Play).

## Testing the core on a PC

`native-test/` builds the same bridge and the same Praat script on Linux/macOS and checks that `chromatic.wav` has
the right order, gaps and pitch:

```bash
scripts/fetch_praat.sh
cmake -S app/src/main/cpp -B build-host -DCHROMATIC_HOST_TEST=ON && cmake --build build-host -j
python3 native-test/make_samples.py /tmp/samples
mkdir -p /tmp/out
build-host/chromatic_host_test app/src/main/assets/chromatic_scale.praat /tmp/samples /tmp/out 0 0 24 0.1 1 1
python3 native-test/check_output.py /tmp/out 0 0 24 0.1 1
```

## License

GPL-3.0 or later (same as the original program, Parselmouth and Praat).

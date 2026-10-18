# Chromatic Scale Generator (Android)

Genera una escala cromática a partir de muestras numeradas (`1.wav`, `2.wav`, …), igual que la versión de PC
(`chromatic_gen.py`, que usaba `wxPython` y `Parselmouth`), pero como app de Android.

## Cómo funciona el port

| PC | Android |
| --- | --- |
| `wxPython` (`app_ui.py`) | Interfaz nativa en Kotlin (`MainActivity.kt`, `res/layout/activity_main.xml`) |
| `Parselmouth` (Python + Praat) | El **mismo código fuente de Praat que usa Parselmouth**, compilado con el NDK (C++) y llamado por JNI |
| `parselmouth.praat.call(...)` | Script de Praat con esos mismos comandos: `app/src/main/assets/chromatic_scale.praat` |
| `DirPickerCtrl` | Selector de carpetas de Android (no necesita permisos de almacenamiento) |

Android no tiene Python ni deja ejecutar Parselmouth tal cual, así que `scripts/fetch_praat.sh` descarga el código
fuente de Parselmouth (que incluye Praat 6.4.16 y `fmt`, con commits fijos) y `app/src/main/cpp/CMakeLists.txt`
compila Praat para Android reutilizando los `CMakeLists` de Parselmouth (sin GUI, audio ni red).
`praat_core.cpp` hace lo mismo que `parselmouth.praat.run`: inicializa Praat y ejecuta un script capturando su salida.

La app copia las muestras de la carpeta elegida a su almacenamiento privado, las procesa con Praat y copia de vuelta
`chromatic.wav` y `pitched_samples/` a esa misma carpeta.

## Compilar el APK

Lo hace GitHub Actions (`.github/workflows/build.yml`): en cada push a `main`/`master`, en cada pull request, o a mano
desde **Actions → Build APK → Run workflow**. Genera tres APK en el artefacto `ChromaticScaleGenerator-apk`:

- `app-universal-release.apk`: `armeabi-v7a` + `arm64-v8a` (sirve en cualquier teléfono)
- `app-arm64-v8a-release.apk`: solo 64 bits
- `app-armeabi-v7a-release.apk`: solo 32 bits

Si subes una etiqueta `v*` (por ejemplo `v1.0`), además se publica un Release con los APK.
La primera compilación tarda bastante (compila todo Praat); las siguientes usan `ccache`.

En local: `scripts/fetch_praat.sh && ./gradlew assembleRelease` (requiere JDK 17, Android SDK y NDK 27.2.12479018).
Los APK se firman con la clave de depuración, así que se pueden instalar directamente.

## Probar el núcleo en PC

`native-test/` compila el mismo puente y el mismo script de Praat en Linux/macOS y comprueba que `chromatic.wav`
tenga el orden, los silencios y el tono correctos:

```bash
scripts/fetch_praat.sh
cmake -S app/src/main/cpp -B build-host -DCHROMATIC_HOST_TEST=ON && cmake --build build-host -j
python3 native-test/make_samples.py /tmp/muestras
mkdir -p /tmp/salida
build-host/chromatic_host_test app/src/main/assets/chromatic_scale.praat /tmp/muestras /tmp/salida 0 0 24 0.1 1 1
python3 native-test/check_output.py /tmp/salida 0 0 24 0.1 1
```

## Licencia

GPL-3.0 o posterior (igual que el programa original, Parselmouth y Praat).

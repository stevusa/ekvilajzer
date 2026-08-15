# Xwave 210 Equalizer — Linux build

Ovaj paket je podešen za Linux build.

## Zahtevi

- JDK 17+
- Android SDK
- Android platform 36
- Android Build Tools 36.0.0
- Internet pri prvom buildu (Gradle/dependencies)

`build-apk.sh` automatski:
1. proverava Javu,
2. pronalazi Android SDK,
3. instalira potrebne SDK pakete ako postoji `sdkmanager`,
4. preuzima Gradle 9.5.0 lokalno u `.tools/`,
5. radi `assembleDebug`,
6. kopira APK u `APK/Xwave210-Equalizer.apk`.

## Debian / Ubuntu

```bash
chmod +x install-linux-deps.sh build-apk.sh
./install-linux-deps.sh

export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
./build-apk.sh
```

## Rezultat

```text
APK/Xwave210-Equalizer.apk
```

## Ako Android SDK nije u ~/Android/Sdk

Na primer:

```bash
export ANDROID_SDK_ROOT="/putanja/do/Android/Sdk"
./build-apk.sh
```

## Napomena o EQ-u

Build može biti uspešan iako određeni TV Box firmware kasnije blokira globalni
AudioEffect na session 0. To je runtime ograničenje firmware-a, ne problem kompajliranja.

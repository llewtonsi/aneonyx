# VixLauncher

Minimal Android home launcher built with Kotlin and Jetpack Compose.

## Build

Install JDK 17 and Android SDK Platform 35 (including Android build tools), then run:

```sh
./gradlew assembleDebug
```

The debug APK is written as `app/build/outputs/vixlauncher/VixLauncher.apk` (Gradle also keeps its
standard `app/build/outputs/apk/debug/app-debug.apk` output). Install it on a connected device
with `adb install -r app/build/outputs/vixlauncher/VixLauncher.apk`, then choose **VixLauncher** in
Android's Home app chooser or under Default apps > Home app.

Open this directory in Android Studio to sync and run the app from the IDE. Android Studio can
configure the SDK location automatically; command-line builds can use `ANDROID_HOME` or
`local.properties` for the SDK path.

## License

VixLauncher is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE). You may use,
study, modify, and share the code for noncommercial purposes under the license terms. Commercial
use, including selling the software or using it for a commercial purpose, is not permitted without
separate permission from the copyright holder. Third-party dependencies remain under their
respective licenses.

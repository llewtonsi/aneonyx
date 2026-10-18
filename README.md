# ⌘ VixLauncher

A minimal Android home launcher with a **Linux terminal-inspired interface**.

VixLauncher brings a shell-like prompt, a fast text-first app list, and a collapsible system readout to your Android home screen. The prompt uses the device's available user and device names instead of a hard-coded identity.

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="VixLauncher app icon" width="144">
</p>

> **A home screen with an Arch-inspired feel, built for Android.**

---

## ✨ Features

### ⌨️ Terminal-inspired home screen

* A shell-style prompt that reads the available Android profile name and device name
* Text-first app rows without app icons
* Live app count and a search field for filtering installed apps
* Press the keyboard's **Search** action to open the first matching app
* Long-press an app to open its Android app info page
* The launcher refreshes its app list when apps are installed, removed, or updated

### 📊 Collapsible `SYS://VITALS` panel

Open the panel to view device readouts:

* 🔋 Battery level and charging state
* 🧠 Device RAM usage
* 💾 Shared internal storage usage and free space
* ⚙️ CPU load average and available core count
* 🌐 Device-wide network receive and transmit rates

The panel stays idle while collapsed and samples while expanded and visible. Network rates are based on device-wide traffic counters. The CPU bar is a normalized load-average estimate, not a direct CPU utilization reading.

---

## 📱 Install on Android

Download `VixLauncher.apk` from the [GitHub Releases page](https://github.com/llewtonsi/vixlauncher/releases) and open it on your phone. Android may ask you to allow installs from that source.

Then make VixLauncher your home app:

1. Open **Settings** on your phone.
2. Go to **Apps → Default apps → Home app**. Menu names can vary by device.
3. Select **VixLauncher**.

Press the Home button to use it. You can switch back to another launcher from the same setting.

---

## 🚀 Build from source

### Requirements

* JDK 17
* Android SDK Platform 35 and Android build tools
* Android 6.0 (API 23) or later to install the app

Clone the repository and build the debug APK:

```bash
git clone https://github.com/llewtonsi/vixlauncher.git
cd vixlauncher
./gradlew assembleDebug
```

The product-named APK is created at:

```text
app/build/outputs/vixlauncher/VixLauncher.apk
```

Gradle also keeps its standard debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

To install the generated APK on a connected device with USB debugging enabled:

```bash
adb install -r app/build/outputs/vixlauncher/VixLauncher.apk
```

You can also open the project in Android Studio, sync Gradle, and run the `app` configuration on a connected phone or emulator.

---

## 🛠️ Built with

* **Kotlin** — application code
* **Jetpack Compose** — user interface
* **Android SDK** — launcher, app discovery, and device information
* **Gradle** — build system

---

## 🤝 Contributing

Bug reports, ideas, and non-commercial code contributions are welcome. Before making a larger change, open an issue to discuss it. Pull requests are reviewed before they are merged; submitting one does not guarantee acceptance.

Please make sure you have permission to contribute any code or assets you submit. Contributions and redistribution remain subject to the project license below.

---

## 📜 License and usage

VixLauncher is provided under the **[PolyForm Noncommercial License 1.0.0](LICENSE)**, the same license used by [Veinfall](https://github.com/llewtonsi/veinfall).

Under its terms, you may study, modify, and share VixLauncher for non-commercial purposes. **Commercial use, including selling the app or using it for a commercial purpose, requires separate permission from the copyright holder.** Third-party dependencies remain under their own licenses.

This is source-available software under a non-commercial license, rather than open source under the standard Open Source Definition, because commercial use is restricted. Read the full [LICENSE](LICENSE) for the applicable terms.

---

⭐ **Thanks for checking out VixLauncher!**

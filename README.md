# Dasher Mobile

Dasher Mobile is an Android implementation of the [Dasher](https://www.inference.org.uk/dasher/) probabilistic text input system, designed for efficient and accessible text entry. Users steer a cursor into characters using touch gestures or device tilt; a statistical language model sizes letters by probability, making common letters easier to reach.

The app works as both a **standalone activity** and a system-wide **Input Method Editor (IME)**.

The architecture diagram is available in [`docs/architecture.puml`](docs/architecture.puml).

---

## Dependencies

| Tool / Library | Version |
|---|---|
| Android Studio | Ladybug (2024.2.1) or newer |
| Android NDK | 27.0.12077973 |
| CMake | 3.22.1 |
| Android SDK | API 24 (min) |
| [DasherCore](https://github.com/janmurin2/DasherCore) | `third_party/DasherCore/` |
| [KenLM](https://github.com/kpu/kenlm) | `third_party/kenlm/` |
| Jetpack Compose / AndroidX | via Gradle |

---

## Building

1. Clone with submodules:
   ```bash
   git clone --recurse-submodules https://github.com/janmurin2/Dasher-Mobile.git
   cd Dasher-Mobile
   ```

2. Open the project in Android Studio and let Gradle sync.

3. Build:
   ```bash
   ./gradlew assembleDebug      # debug APK
   ./gradlew assembleRelease    # release APK (update signing config first)
   ```

CMake compiles the native libraries (`DasherCore`, `KenLM`) automatically as part of the Gradle build.

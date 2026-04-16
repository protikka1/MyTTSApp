🎙️ TTS Pro: Material You Translator & Speech Engine
Version 1.1.0 — A professional, high-performance Text-to-Speech utility for Android.

📱 Project Overview
TTS Pro is a modern Android application built with Jetpack Compose and Material 3. It provides a seamless experience for converting text to speech, translating content on-device, and exporting high-quality audio files.

✨ Key Features
- **Material You Dynamic Theming**: The UI adapts its color palette to your device's wallpaper (Android 12+).
- **Folding Header Interface**: A sleek, space-saving design where the header collapses as you interact with the app.
- **On-Device Translation**: Powered by Google ML Kit, allowing instant translation from English to major languages like Arabic, Bengali, Hindi, Urdu, and more.
- **Audio Export (.wav)**: Save your generated speech directly to your device's "Music/TTSPro" folder using modern Scoped Storage.
- **Granular Voice Control**: Adjust Pitch and Speed via a convenient side-drawer settings menu.
- **Multilingual Support**: Supports over 13 major world languages with automatic voice engine detection.
- **In-App User Guide**: Integrated help documentation accessible directly from the settings drawer.

🛠️ Tech Stack
- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Speech Engine**: Android TextToSpeech (TTS)
- **Machine Learning**: Google ML Kit (Natural Language Translation)
- **Dependency Management**: Gradle Version Catalog (.toml)

🚀 Getting Started
1. **Clone & Build**: Open the project in Android Studio (Ladybug or newer).
2. **Sync Gradle**: Ensure all dependencies from `libs.versions.toml` are downloaded.
3. **Run**: Deploy to a physical device (recommended: Pixel 6+) or a high-performance emulator.

📂 File Structure
- `app/src/main/java/.../MainActivity.kt`: The core logic and UI implementation.
- `app/src/main/assets/user_guide.txt`: Source for the in-app help documentation.
- `gradle/libs.versions.toml`: Centralized dependency and version management.

👨‍💻 Developer Notes
This version (1.1.0) includes significant optimizations for real-time translation and asynchronous audio synthesis. Designed with a focus on modern Android architecture and Material Design 3 standards.

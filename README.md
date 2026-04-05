🎙️ TTS Pro: Material You Translator
Version 1.0.0 A modern, accessible, and multilingual Text-to-Speech utility for Android.

📱 Project Overview
TTS Pro is a lightweight Android application designed with Material 3 (Material You) principles. It allows users to input text, adjust vocal parameters 
(pitch and speed), translate text into multiple languages on-device, and export the resulting audio as shareable .wav files.

✨ Key Features
Material You Theming: Full support for Dynamic Color—the app’s UI and Home Screen icon automatically adapt to the user's wallpaper.

Text-to-Speech Engine: Granular control over speech rate and pitch with a "Voice Selection" menu for various locales.

Offline Translation: Powered by Google ML Kit, allowing for English to Spanish, French, and German translation without an internet connection.

Audio Export & Share: Generates high-quality .wav files using Scoped Storage and the Android Share Sheet (FileProvider) for secure sharing to WhatsApp, 
Gmail, and more.

Dark Mode Native: A manual toggle that respects Material 3 color roles for eye comfort.

🛠️ Tech Stack & Tools
Language: Kotlin

UI Framework: Jetpack Compose (Material 3)

Development Environment: Android Studio (Ladybug/2026) on macOS Monterey (Intel)

Libraries: * androidx.compose.material3 (Theming)

com.google.mlkit:translate (Machine Learning)

androidx.core:core-ktx (FileProvider & System Integration)

🚀 How to Install
Download the TTSPro_v1.apk from the [Releases] section.

Open the file on your Android device (v7.0+).

Allow "Install from Unknown Sources" when prompted.

Launch and enjoy!

👨‍💻 Developer Notes
Developed as a deep-dive into the Android 15/16 ecosystem, focusing on State Management in Compose and Hardware Abstraction (TTS Engine). Optimized 
specifically for Intel-based development environments.

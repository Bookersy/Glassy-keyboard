# Glassy Keyboard

Glassy Keyboard is a native Android input method editor (IME) with:

- OLED black keyboard background
- Rounded translucent glass-style keys
- A suggestion strip for autocorrect and next-word guesses
- A launcher screen that opens Android's keyboard enable/default-picker settings

## Build

Install Android SDK Platform 35, then build a debug APK:

```bash
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This branch also includes a ready-to-install debug APK at:

```text
dist/glassy-keyboard-debug.apk
```

## Install and enable

1. Install the APK on your Android device. If Android prompts you, allow installs from the app you used to download it.
2. Open **Glassy Keyboard**.
3. Tap **Enable Glassy Keyboard** and enable it in Android settings.
4. Return to the app and tap **Choose Default Keyboard**.

# Glassy Keyboard

Glassy Keyboard is a native Android input method editor (IME) with:

- OLED black keyboard background
- Raised squircle glass-style keys with transparent icon controls
- A suggestion strip for autocorrect and next-word guesses
- Vibration, long-press alternates, repeated delete, slide typing, and spacebar cursor movement
- Number/symbol keyboard modes and clipboard history chips with text/image paste support
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

## Controls

- Tap **123** for numbers and common symbols; tap **#+=** for more symbols; tap **ABC** to return.
- Hold letter/punctuation keys for alternate characters.
- Hold delete to repeatedly delete.
- Slide across letters to gesture-type a word.
- Tap the clipboard icon to show recent clipboard items. Text clips paste directly; image clips paste into apps that accept rich image input.
- Hold the spacebar and drag left or right to move the cursor.

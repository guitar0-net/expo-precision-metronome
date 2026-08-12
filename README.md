<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# expo-precision-metronome

[![JS](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-js.yml/badge.svg?branch=main)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-js.yml)
[![Android](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-android.yml/badge.svg?branch=main)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-android.yml)
[![iOS](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-ios.yml/badge.svg?branch=main)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-ios.yml)
[![npm version](https://img.shields.io/npm/v/expo-precision-metronome?style=flat-square)](https://www.npmjs.com/package/expo-precision-metronome)
[![Expo SDK](https://img.shields.io/badge/Expo%20SDK-55%20%7C%2056-000020?style=flat-square&logo=expo&logoColor=white)](https://docs.expo.dev/)
[![REUSE status](https://api.reuse.software/badge/github.com/guitar0-net/expo-precision-metronome)](https://api.reuse.software/info/github.com/guitar0-net/expo-precision-metronome)

> High-precision metronome engine for Expo and React Native. Beats are scheduled at the **native audio layer** — timing stays rock-solid regardless of JS thread load.

## Features

- Sample-accurate beat scheduling via AVAudioEngine (iOS) and [Oboe](https://github.com/google/oboe) (Android)
- `onBeat` event with beat index, high-resolution timestamp, and accent level
- `onStop` event distinguishing explicit stop from audio interruption (phone call, alarm, etc.)
- Live BPM change without restarting the engine
- 6 synthesized sound presets switchable on the fly (`click`, `beep`, `woodblock`, `rim`, `hihat`, `cowbell`)
- Accent patterns — up to 16 beats, each independently `strong`, `normal`, or `muted`, changeable on the fly
- JSI bridge — no JSON serialization overhead
- Full TypeScript types included

## Requirements

|              | Minimum                                          |
| ------------ | ------------------------------------------------ |
| Expo SDK     | 55 (developed and tested against 56)             |
| React Native | 0.82                                             |
| React        | 19                                               |
| iOS          | 15.1 on SDK 55, 16.4 on SDK 56                   |
| Android API  | 24 (26+ recommended for AAudio low-latency path) |
| Node         | 20.19.4 (React Native 0.85 toolchain)            |

## Installation

```sh
npx expo install expo-precision-metronome
```

> [!NOTE]
> This package requires native code. It does **not** work with Expo Go — use a [development build](https://docs.expo.dev/develop/development-builds/introduction/).

## Background playback

By default the metronome only plays while your app is in the foreground. Nothing stops it the moment you background the app, but nothing protects it either: on Android the process drops to the `cached` bucket and is the first thing the low-memory killer takes, and on iOS the app is suspended a few seconds after it leaves the screen. Emulators and simulators hide this — real devices do not.

To keep playing, opt in with the config plugin:

```json
{
  "expo": {
    "plugins": [["expo-precision-metronome", { "backgroundAudio": true }]]
  }
}
```

That adds a `mediaPlayback` foreground service plus its permissions on Android, and the `audio` background mode on iOS. It is opt-in because those permissions show up in your Play Store listing — apps that only need foreground playback should not pay for them.

Then ask for it per playback session:

```ts
await start(120, { background: true });

// or customise the Android notification
await start(120, {
  background: {
    title: "Practice",
    text: "4/4 at 120 BPM",
    color: "#f59e0b",
  },
});
```

Without the plugin, passing `background` rejects with `ERR_BACKGROUND_NOT_CONFIGURED` rather than silently playing in the foreground only.

### What you get, and what you don't

|         | Behaviour                                                                                   |
| ------- | ------------------------------------------------------------------------------------------- |
| Android | Ongoing notification with a Stop button; the process is held at foreground-service priority |
| iOS     | Playback continues for as long as audio is actually being produced                          |

Known limits:

- **Swiping the app out of recents stops the metronome.** The audio engine lives with the JS context, which is destroyed along with the task.
- **No lock screen transport controls.** A `MediaSession` would compete with the music app you are practising along to for the media widget and headset buttons, so the library deliberately does not register one.
- **Android 13+** gates the notification behind `POST_NOTIFICATIONS`. Request it from your app; if the user declines, playback still works — the notification is just hidden from the shade.
- **iOS App Review** expects apps declaring `UIBackgroundModes: audio` to genuinely use it.

### Playing over a backing track

By default starting the metronome interrupts audio from other apps. Pass `mixWithOthers` to keep the backing track playing:

```ts
await start(120, { background: true, mixWithOthers: true });
```

On iOS this activates the session with `.mixWithOthers`; on Android it requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so other audio ducks rather than stops. Either way the metronome still yields to a phone call.

## Usage

```tsx
import { useEffect } from "react";
import { start, stop, setBpm, setSound, setPattern } from "expo-precision-metronome";
import ExpoPrecisionMetronomeModule from "expo-precision-metronome";

export default function Metronome() {
  useEffect(() => {
    const beatSub = ExpoPrecisionMetronomeModule.addListener(
      "onBeat",
      ({ beat, timestamp, accent }) => {
        console.log(`Beat ${beat} (${accent}) at ${timestamp}s`);
      },
    );

    const stopSub = ExpoPrecisionMetronomeModule.addListener("onStop", ({ reason }) => {
      console.log(`Stopped: ${reason}`);
    });

    setSound("woodblock");
    setPattern(["strong", "normal", "normal", "normal"]); // 4/4
    start(120);

    return () => {
      stop();
      beatSub.remove();
      stopSub.remove();
    };
  }, []);
}
```

### Accent patterns

`setPattern()` defines a repeating accent pattern of up to 16 beats. Each beat is independently set to one of three levels:

| Level    | Character                                   |
| -------- | ------------------------------------------- |
| `strong` | Higher pitch, louder, punchier decay        |
| `normal` | Standard click                              |
| `muted`  | Ghost note — same timbre at ~12 % amplitude |

The pattern loops automatically. It takes effect immediately without restarting the engine.

```tsx
import { setPattern } from "expo-precision-metronome";

// 4/4 — downbeat accent (this is the default)
await setPattern(["strong", "normal", "normal", "normal"]);

// 3/4 waltz
await setPattern(["strong", "normal", "normal"]);

// 6/8 compound time — accent on beats 1 and 4
await setPattern(["strong", "muted", "muted", "normal", "muted", "muted"]);

// Ghost groove — strong downbeat, ghost on beat 3
await setPattern(["strong", "normal", "muted", "normal"]);

// 16-step pattern (maximum length)
await setPattern([
  "strong",
  "muted",
  "normal",
  "muted",
  "normal",
  "muted",
  "strong",
  "muted",
  "normal",
  "muted",
  "normal",
  "muted",
  "normal",
  "muted",
  "normal",
  "muted",
]);

// Change on the fly while the engine is running
await setPattern(["strong", "normal", "normal"]); // switch to 3/4 mid-song
```

## API

### Functions

#### `start(bpm: number, options?: StartOptions): Promise<void>`

Starts the metronome at the given BPM. Resolves when the audio engine has started. Throws `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`, `TypeError` if `options` are malformed, and rejects with `ERR_BACKGROUND_NOT_CONFIGURED` if `background` is requested without the config plugin. See [Background playback](#background-playback).

| Option          | Type                           | Default | Description                                                 |
| --------------- | ------------------------------ | ------- | ----------------------------------------------------------- |
| `background`    | `boolean \| BackgroundOptions` | `false` | Keep playing while backgrounded. Requires the config plugin |
| `mixWithOthers` | `boolean`                      | `false` | Duck other apps' audio instead of interrupting it           |

`BackgroundOptions` — everything except `title` and `text` is Android-only and ignored on iOS:

| Field                  | Type                                | Default       | Description                                         |
| ---------------------- | ----------------------------------- | ------------- | --------------------------------------------------- |
| `title`                | `string`                            | `"Metronome"` | Notification title                                  |
| `text`                 | `string`                            | `"{bpm} BPM"` | Notification body; the default follows `setBpm()`   |
| `icon`                 | `string`                            | app icon      | Android: drawable or mipmap resource name           |
| `color`                | `string`                            | —             | Android: accent colour, e.g. `"#f59e0b"`            |
| `stopLabel`            | `string`                            | `"Stop"`      | Android: label of the stop button                   |
| `showStopButton`       | `boolean`                           | `true`        | Android: render the stop button                     |
| `channelName`          | `string`                            | `"Metronome"` | Android: channel name in system settings            |
| `lockscreenVisibility` | `"public" \| "private" \| "secret"` | `"public"`    | Android: notification visibility on the lock screen |

#### `stop(): Promise<void>`

Stops the metronome. Emits `onStop` with `reason: "explicit"`.

#### `setBpm(bpm: number): Promise<void>`

Changes the tempo on the fly without stopping the engine. Throws `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`.

#### `setSound(sound: SoundPreset): Promise<void>`

Switches the click sound without stopping the engine. The new preset takes effect on the next beat. Throws `TypeError` if `sound` is not one of the valid presets. Default is `"click"`.

#### `setPattern(pattern: BeatAccent[]): Promise<void>`

Sets the accent pattern. `pattern` must contain 1–16 `BeatAccent` values. The pattern loops indefinitely — beat index 0 corresponds to the first element, beat index `n` to `pattern[n % pattern.length]`. Can be called while the engine is running; the new pattern takes effect from the next beat. Throws `RangeError` if the length is out of range, `TypeError` if any element is invalid. Default pattern is `["strong", "normal", "normal", "normal"]`.

---

### Events

Subscribe via `ExpoPrecisionMetronomeModule.addListener(eventName, handler)`. Always call `.remove()` on the returned subscription to avoid leaks.

#### `onBeat`

Emitted on every beat.

| Property    | Type         | Description                                               |
| ----------- | ------------ | --------------------------------------------------------- |
| `beat`      | `number`     | Beat index, starting at 0, increments each beat           |
| `timestamp` | `number`     | High-resolution audio clock timestamp (seconds)           |
| `accent`    | `BeatAccent` | Accent level of this beat: `strong`, `normal`, or `muted` |

#### `onStop`

Emitted when the metronome stops for any reason.

| Property | Type                                             | Description                                                                                                                                                                                                  |
| -------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `reason` | `"explicit" \| "interruption" \| "notification"` | `"explicit"` — stopped by `stop()`. `"interruption"` — stopped by the OS (incoming call, audio focus loss, headphones unplugged). `"notification"` — Android only, the user pressed Stop in the notification |

---

### Constants

| Constant                  | Value                                                  | Description                            |
| ------------------------- | ------------------------------------------------------ | -------------------------------------- |
| `BPM_MIN`                 | `20`                                                   | Minimum valid BPM                      |
| `BPM_MAX`                 | `300`                                                  | Maximum valid BPM                      |
| `SOUND_PRESETS`           | `["click","beep","woodblock","rim","hihat","cowbell"]` | All available sound presets            |
| `BEAT_ACCENTS`            | `["strong","normal","muted"]`                          | All valid accent levels                |
| `BEAT_PATTERN_MAX_LENGTH` | `16`                                                   | Maximum beats in a pattern             |
| `DEFAULT_BEAT_PATTERN`    | `["strong","normal","normal","normal"]`                | Default pattern used when none is set  |
| `LOCKSCREEN_VISIBILITIES` | `["public","private","secret"]`                        | Valid Android lock screen visibilities |

---

### Types

```ts
type BeatAccent = "strong" | "normal" | "muted";

type BeatEventPayload = {
  beat: number;
  timestamp: number;
  accent: BeatAccent;
};

type StopEventPayload = {
  reason: "explicit" | "interruption" | "notification";
};

type SoundPreset = "click" | "beep" | "woodblock" | "rim" | "hihat" | "cowbell";

type LockscreenVisibility = "public" | "private" | "secret";

type BackgroundOptions = {
  title?: string;
  text?: string;
  icon?: string;
  color?: string;
  stopLabel?: string;
  showStopButton?: boolean;
  channelName?: string;
  lockscreenVisibility?: LockscreenVisibility;
};

type StartOptions = {
  background?: boolean | BackgroundOptions;
  mixWithOthers?: boolean;
};
```

#### Accent levels

| Level    | Volume   | Pitch          | Decay          |
| -------- | -------- | -------------- | -------------- |
| `strong` | +30 %    | ×1.4 freq      | 0.6× faster    |
| `normal` | baseline | baseline       | baseline       |
| `muted`  | −88 %    | same as normal | same as normal |

#### Sound presets

| Preset      | Character               | Duration |
| ----------- | ----------------------- | -------- |
| `click`     | 1 kHz sine, fast decay  | 10 ms    |
| `beep`      | 880 Hz sine, soft       | 20 ms    |
| `woodblock` | 400 Hz, very percussive | 8 ms     |
| `rim`       | 800 + 1600 Hz dual sine | 6 ms     |
| `hihat`     | Noise burst             | 8 ms     |
| `cowbell`   | 562 + 845 Hz, long      | 250 ms   |

## Running the example app

The example in `example/` is a standalone Expo SDK 56 app that autolinks the package from `..`.

```sh
cd example
npm install

# iOS
npx expo run:ios

# Android
npx expo run:android
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT © [Andrey Kotlyar](https://github.com/kotlyar-andrey)

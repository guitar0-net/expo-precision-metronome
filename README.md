<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# expo-precision-metronome

[![JS](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-js.yml/badge.svg)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-js.yml)
[![Android](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-android.yml/badge.svg)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-android.yml)
[![iOS](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-ios.yml/badge.svg)](https://github.com/guitar0-net/expo-precision-metronome/actions/workflows/ci-ios.yml)
[![npm version](https://img.shields.io/npm/v/expo-precision-metronome?style=flat-square)](https://www.npmjs.com/package/expo-precision-metronome)
[![Expo SDK](https://img.shields.io/badge/Expo%20SDK-55%2B-000020?style=flat-square&logo=expo&logoColor=white)](https://docs.expo.dev/)
[![REUSE status](https://api.reuse.software/badge/github.com/guitar0-net/expo-precision-metronome)](https://api.reuse.software/info/github.com/guitar0-net/expo-precision-metronome)

> High-precision metronome engine for Expo and React Native. Beats are scheduled at the **native audio layer** — timing stays rock-solid regardless of JS thread load.

## Features

- Sample-accurate beat scheduling via AVAudioEngine (iOS) and [Oboe](https://github.com/google/oboe) (Android)
- `onBeat` event with beat index and high-resolution timestamp
- `onStop` event distinguishing explicit stop from audio interruption (phone call, alarm, etc.)
- Live BPM change without restarting the engine
- 6 synthesized sound presets switchable on the fly (`click`, `beep`, `woodblock`, `rim`, `hihat`, `cowbell`)
- JSI bridge — no JSON serialization overhead
- Full TypeScript types included

## Requirements

|             | Minimum                                          |
| ----------- | ------------------------------------------------ |
| Expo SDK    | 55                                               |
| iOS         | 15.1                                             |
| Android API | 24 (26+ recommended for AAudio low-latency path) |
| Node        | 18                                               |

## Installation

```sh
npx expo install expo-precision-metronome
```

> [!NOTE]
> This package requires native code. It does **not** work with Expo Go — use a [development build](https://docs.expo.dev/develop/development-builds/introduction/).

## Usage

```tsx
import { useEffect } from "react";
import { start, stop, setBpm, setSound } from "expo-precision-metronome";
import ExpoPrecisionMetronomeModule from "expo-precision-metronome";

export default function Metronome() {
  useEffect(() => {
    const beatSub = ExpoPrecisionMetronomeModule.addListener(
      "onBeat",
      ({ beat, timestamp }) => {
        console.log(`Beat ${beat} at ${timestamp}s`);
      },
    );

    const stopSub = ExpoPrecisionMetronomeModule.addListener("onStop", ({ reason }) => {
      console.log(`Stopped: ${reason}`);
    });

    setSound("woodblock");
    start(120);

    return () => {
      stop();
      beatSub.remove();
      stopSub.remove();
    };
  }, []);
}
```

## API

### Functions

#### `start(bpm: number): Promise<void>`

Starts the metronome at the given BPM. Resolves when the audio engine has started. Throws `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`.

#### `stop(): Promise<void>`

Stops the metronome. Emits `onStop` with `reason: "explicit"`.

#### `setBpm(bpm: number): Promise<void>`

Changes the tempo on the fly without stopping the engine. Throws `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`.

#### `setSound(sound: SoundPreset): Promise<void>`

Switches the click sound without stopping the engine. The new preset takes effect on the next beat. Throws `TypeError` if `sound` is not one of the valid presets. Default is `"click"`.

---

### Events

Subscribe via `ExpoPrecisionMetronomeModule.addListener(eventName, handler)`. Always call `.remove()` on the returned subscription to avoid leaks.

#### `onBeat`

Emitted on every beat.

| Property    | Type     | Description                                     |
| ----------- | -------- | ----------------------------------------------- |
| `beat`      | `number` | Beat index, starting at 1                       |
| `timestamp` | `number` | High-resolution audio clock timestamp (seconds) |

#### `onStop`

Emitted when the metronome stops for any reason.

| Property | Type                           | Description                                                                                                                |
| -------- | ------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| `reason` | `"explicit" \| "interruption"` | `"explicit"` — stopped by `stop()`. `"interruption"` — stopped by the OS (incoming call, audio session interruption, etc.) |

---

### Constants

| Constant        | Value                                                  | Description           |
| --------------- | ------------------------------------------------------ | --------------------- |
| `BPM_MIN`       | `20`                                                   | Minimum valid BPM     |
| `BPM_MAX`       | `300`                                                  | Maximum valid BPM     |
| `SOUND_PRESETS` | `["click","beep","woodblock","rim","hihat","cowbell"]` | All available presets |

---

### Types

```ts
type BeatEventPayload = {
  beat: number;
  timestamp: number;
};

type StopEventPayload = {
  reason: "explicit" | "interruption";
};

type SoundPreset = "click" | "beep" | "woodblock" | "rim" | "hihat" | "cowbell";
```

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

```sh
cd example

# iOS
npx expo run:ios

# Android
npx expo run:android
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT © [Andrey Kotlyar](https://github.com/kotlyar-andrey)

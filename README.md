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
- `pause()` / `resume()` that keep the audio engine alive, so resuming is immediate and sample-accurate
- `onPlaybackChange` event carrying both the new state and why it changed — your own call, the notification, or the OS
- Android notification with **Pause ↔ Resume** and **Stop**, so a session can be controlled from the lock screen
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

> [!IMPORTANT]
> Upgrading from 1.x? `onStop` is replaced by `onPlaybackChange`, and audio interruptions now pause instead of stopping. See [MIGRATION.md](MIGRATION.md).

## Background playback

`background` asks the OS to **protect** playback, not to enable it. Nothing stops the metronome the moment you background the app — but without this, nothing keeps it alive either: on Android the process drops to the `cached` bucket and is the first thing the low-memory killer takes, and on iOS the app is suspended a few seconds after it leaves the screen. Emulators and simulators hide this — real devices do not.

To keep playing, opt in with the config plugin:

```json
{
  "expo": {
    "plugins": [["expo-precision-metronome", { "backgroundAudio": true }]]
  }
}
```

That adds a `mediaPlayback` foreground service plus its permissions on Android, and the `audio` background mode on iOS. It is opt-in because those permissions show up in your Play Store listing — apps that only need foreground playback should not pay for them.

> [!IMPORTANT]
> Config plugins only run during `npx expo prebuild` — EAS Build does this for you. If your project keeps `android/` and `ios/` in version control and never prebuilds, the plugin never applies. Run `npx expo prebuild` once (it merges into existing native projects), or add the entries by hand: `UIBackgroundModes: audio` in `Info.plist`, and in `AndroidManifest.xml` the `net.guitar0.metronome.MetronomeService` declaration plus the `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS` permissions.

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

|         | Behaviour                                                                                                     |
| ------- | ------------------------------------------------------------------------------------------------------------- |
| Android | Ongoing notification with **Pause ↔ Resume** and **Stop**; the process is held at foreground-service priority |
| iOS     | Playback continues for as long as audio is actually being produced                                            |

The notification renders in two modes. While your app is on screen it shows the title and the buttons only; once the app is backgrounded it adds the current tempo as `"{bpm} BPM"`. The number the user can already read from your own UI is the one number that could go stale, so it is simply not drawn there. A `text` you supply yourself is static, so it is shown in both modes.

There is no pause UI on iOS. That is a decision, not a platform limit — see the `MediaSession` note below.

Known limits:

- **`background` is a per-call request, but the entitlement it needs is app-wide.** Once the plugin is enabled, `UIBackgroundModes: audio` applies to the whole iOS app, so a session started _without_ `background` also keeps playing after you leave the screen — it just has no notification and no Android foreground service behind it. Call `stop()` from your own `AppState` handler if a session must not outlive the foreground.
- **Swiping the app out of recents stops the metronome.** The audio engine lives with the JS context, which is destroyed along with the task. The notification looks like a player, so tell your users otherwise if it matters — players usually survive this and the metronome does not.
- **No media widget or headset transport controls.** A `MediaSession` (Android) or `MPNowPlayingInfoCenter` (iOS) would give the notification, the lock screen, Wear and Control Center for free — and would capture the headset play/pause button and the media carousel slot from the backing track you are practising along to. Incompatible with `mixWithOthers`, so the library deliberately registers neither. This is also why there is no pause UI on iOS.
- **Android 13+** gates the notification behind `POST_NOTIFICATIONS`. Call [`requestNotificationPermission()`](#requestnotificationpermission-promiseboolean) before `start({ background })`; if the user declines, playback still works — but the notification is hidden, so pause and stop are only reachable from inside your app. `getState().notificationVisible` tells you whether that happened.
- **iOS App Review** expects apps declaring `UIBackgroundModes: audio` to genuinely use it.

### Playing over a backing track

By default starting the metronome interrupts audio from other apps. Pass `mixWithOthers` to keep the backing track playing:

```ts
await start(120, { background: true, mixWithOthers: true });
```

On iOS this activates the session with `.mixWithOthers`; on Android it requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so other audio ducks rather than stops. Either way the metronome still yields to a phone call.

## Usage

```tsx
import { useEffect, useState } from "react";
import {
  getState,
  setPattern,
  setSound,
  start,
  stop,
  type PlaybackState,
} from "expo-precision-metronome";
import ExpoPrecisionMetronomeModule from "expo-precision-metronome";

export default function Metronome() {
  const [playback, setPlayback] = useState<PlaybackState>("stopped");

  useEffect(() => {
    const beatSub = ExpoPrecisionMetronomeModule.addListener(
      "onBeat",
      ({ beat, timestamp, accent }) => {
        console.log(`Beat ${beat} (${accent}) at ${timestamp}s`);
      },
    );

    // Playback can change without you asking: the notification buttons and audio
    // interruptions are both command sources of their own.
    const playbackSub = ExpoPrecisionMetronomeModule.addListener(
      "onPlaybackChange",
      ({ state, reason }) => {
        console.log(`Playback ${state}: ${reason}`);
        setPlayback(state);
      },
    );

    // Events only cover transitions you were mounted for. Reconcile on mount, or a
    // remount while backgrounded leaves your UI claiming the metronome is playing.
    getState().then(({ state }) => setPlayback(state));

    setSound("woodblock");
    setPattern(["strong", "normal", "normal", "normal"]); // 4/4
    start(120);

    return () => {
      stop();
      beatSub.remove();
      playbackSub.remove();
    };
  }, []);
}
```

### Pausing

`pause()` silences the click without tearing the audio stream down, so `resume()` re-enters with the same sample-accurate timing as `start()` — no rebuild, no audible gap.

```ts
await pause();
await resume(); // re-enters on the downbeat
```

Both are idempotent: `pause()` does nothing unless playback is running, `resume()` does nothing unless it is paused, and neither throws when the metronome has already stopped. That matters because they are not the only command source — the Android notification issues the same commands, so a call that lost the race has to be harmless rather than invert into its opposite. For the same reason there is no `toggle()`.

**`resume()` restarts the bar.** The beat counter resets, so playback always re-enters on "one". Pause is pressed by ear at an arbitrary moment and a musician does not re-enter mid-bar. A bar number derived from `beat` (`Math.floor(beat / 4)`) restarts with it.

Resources behave differently on each platform, deliberately:

|                        | Android                                              | iOS                          |
| ---------------------- | ---------------------------------------------------- | ---------------------------- |
| Audio focus / session  | Focus is **released** and re-requested on `resume()` | The session **stays active** |
| Notification / service | Held — the notification is the resume affordance     | n/a                          |
| Audio stream           | Kept open, writing silence                           | Kept open, writing silence   |

Holding audio focus while silent would keep a backing track ducked for no reason, which is exactly what `mixWithOthers` exists to avoid. iOS cannot do the same: deactivating the session stops background audio output, the app is suspended, and neither `resume()` nor the end-of-interruption notification would ever arrive. The visible consequence on iOS is that with `mixWithOthers: false` a paused metronome still silences other audio.

### Interruptions

The metronome follows platform convention rather than treating every interruption as a stop:

| Cause                                              | Result                                                           |
| -------------------------------------------------- | ---------------------------------------------------------------- |
| Headphones unplugged (Android)                     | **Pauses.** Never auto-resumed — the user pulled the plug        |
| Incoming call / transient focus loss               | **Pauses**, then auto-resumes when the interruption ends cleanly |
| Another app takes over audio permanently (Android) | **Stops**                                                        |
| iOS interruption ends without `.shouldResume`      | Stays paused — iOS is telling you not to resume                  |

Every one of these arrives as `onPlaybackChange` with `reason: "interruption"`, so a UI that follows the event needs no special handling.

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

Starts the metronome at the given BPM. Resolves when the audio engine has started. Rejects with `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`, `TypeError` if `options` are malformed, and `ERR_BACKGROUND_NOT_CONFIGURED` if `background` is requested without the config plugin. See [Background playback](#background-playback).

Calling `start()` on a metronome that is already running **or paused** restarts it so the new `bpm` and `options` take effect; the restart is silent, so no `onPlaybackChange` is emitted. Use `setBpm()` to change tempo without the gap.

| Option          | Type                           | Default | Description                                                 |
| --------------- | ------------------------------ | ------- | ----------------------------------------------------------- |
| `background`    | `boolean \| BackgroundOptions` | `false` | Keep playing while backgrounded. Requires the config plugin |
| `mixWithOthers` | `boolean`                      | `false` | Duck other apps' audio instead of interrupting it           |

`BackgroundOptions` — everything except `title` and `text` is Android-only and ignored on iOS:

| Field                  | Type                                | Default       | Description                                                                                                                         |
| ---------------------- | ----------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `title`                | `string`                            | `"Metronome"` | Notification title                                                                                                                  |
| `text`                 | `string`                            | `"{bpm} BPM"` | Notification body. The default follows `setBpm()` and is shown only while your app is backgrounded; a value you set is shown always |
| `icon`                 | `string`                            | app icon      | Android: drawable or mipmap resource name                                                                                           |
| `color`                | `string`                            | —             | Android: accent colour, e.g. `"#f59e0b"`                                                                                            |
| `stopLabel`            | `string`                            | `"Stop"`      | Android: label of the stop button                                                                                                   |
| `pauseLabel`           | `string`                            | `"Pause"`     | Android: label of the pause button                                                                                                  |
| `resumeLabel`          | `string`                            | `"Resume"`    | Android: label the pause button takes while paused                                                                                  |
| `showStopButton`       | `boolean`                           | `true`        | Android: render the stop button. The pause button is always rendered                                                                |
| `channelName`          | `string`                            | `"Metronome"` | Android: channel name in system settings                                                                                            |
| `lockscreenVisibility` | `"public" \| "private" \| "secret"` | `"public"`    | Android: notification visibility on the lock screen                                                                                 |

#### `stop(): Promise<void>`

Stops the metronome and tears the audio engine down. Emits `onPlaybackChange` with `state: "stopped"`, `reason: "explicit"`. A no-op when already stopped, and a no-op emits nothing.

#### `pause(): Promise<void>`

Silences the metronome while keeping the engine alive. A no-op unless playback is running. See [Pausing](#pausing).

#### `resume(): Promise<void>`

Resumes a paused metronome **on the downbeat** — the beat counter resets. A no-op unless playback is paused; in particular it does not throw when the metronome has already stopped.

#### `getState(): Promise<MetronomeState>`

Reads the current state. `onPlaybackChange` covers transitions, not a listener that was not mounted for one — with playback controllable from the notification, a component that remounted while your app was backgrounded has no other way to find out what happened. Call it on mount to reconcile.

| Property              | Type            | Description                                                                                                                                                                    |
| --------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `state`               | `PlaybackState` | `"running"`, `"paused"` or `"stopped"`                                                                                                                                         |
| `bpm`                 | `number`        | Current tempo; `0` before the first `start()`                                                                                                                                  |
| `notificationVisible` | `boolean`       | Whether the Android notification is actually on screen. `false` on iOS, and on Android when notifications are switched off — pause is then reachable only from inside your app |

#### `requestNotificationPermission(): Promise<boolean>`

Asks for `POST_NOTIFICATIONS` on Android 13+ and resolves to whether it is now granted. Resolves `true` immediately on iOS and below Android 13.

The library never prompts on its own: a system dialog thrown at the first `start({ background })` lands at a moment your app cannot predict, and on Android 13 a second denial means it never appears again. Call this when it suits your flow.

Rejects with `ERR_NO_FOREGROUND_ACTIVITY` when no activity is on screen — Android cannot show the dialog then, and answering `false` would spend the permission's one undetermined state on a dialog the user never saw.

#### `getNotificationPermission(): Promise<NotificationPermission>`

Reads the permission without ever prompting.

| Property      | Type               | Description                                                                     |
| ------------- | ------------------ | ------------------------------------------------------------------------------- |
| `status`      | `PermissionStatus` | `"granted"`, `"denied"` or `"undetermined"`                                     |
| `canAskAgain` | `boolean`          | Whether `requestNotificationPermission()` would still surface the system dialog |

```ts
const { status, canAskAgain } = await getNotificationPermission();
if (status !== "granted" && canAskAgain) {
  await requestNotificationPermission();
}
```

Gate on `canAskAgain` rather than on `status === "undetermined"`. Android 13 allows two denials before the dialog stops appearing, so `"denied"` covers both a state worth asking from again and one that is final — and a check for `"undetermined"` gives up after the first denial, which is exactly the retry that wins back a user who declined by reflex.

#### `setBpm(bpm: number): Promise<void>`

Changes the tempo on the fly without stopping the engine. Throws `RangeError` if `bpm` is outside `BPM_MIN`–`BPM_MAX`. Applies in every state: while paused it is banked and takes effect on `resume()`.

#### `setSound(sound: SoundPreset): Promise<void>`

Switches the click sound without stopping the engine. The new preset takes effect on the next beat. Throws `TypeError` if `sound` is not one of the valid presets. Default is `"click"`.

#### `setPattern(pattern: BeatAccent[]): Promise<void>`

Sets the accent pattern. `pattern` must contain 1–16 `BeatAccent` values. The pattern loops indefinitely — beat index 0 corresponds to the first element, beat index `n` to `pattern[n % pattern.length]`. Can be called while the engine is running; the new pattern takes effect from the next beat. Throws `RangeError` if the length is out of range, `TypeError` if any element is invalid. Default pattern is `["strong", "normal", "normal", "normal"]`.

---

### Events

Subscribe via `ExpoPrecisionMetronomeModule.addListener(eventName, handler)`. Always call `.remove()` on the returned subscription to avoid leaks.

#### `onBeat`

Emitted on every beat.

| Property    | Type         | Description                                                         |
| ----------- | ------------ | ------------------------------------------------------------------- |
| `beat`      | `number`     | Beat index, starting at 0, counted from `start()` **or** `resume()` |
| `timestamp` | `number`     | High-resolution audio clock timestamp (seconds)                     |
| `accent`    | `BeatAccent` | Accent level of this beat: `strong`, `normal`, or `muted`           |

#### `onPlaybackChange`

Emitted on every real transition between `running`, `paused` and `stopped`, whoever caused it. A command that changes nothing — `pause()` on an already-paused metronome, a double-tap in the notification shade — emits **nothing**, so a consumer counting transitions cannot drift.

| Property | Type                                             | Description                                                                                                                                                                                                 |
| -------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `state`  | `"running" \| "paused" \| "stopped"`             | The state now in effect                                                                                                                                                                                     |
| `reason` | `"explicit" \| "interruption" \| "notification"` | `"explicit"` — your own `stop()`, `pause()` or `resume()`. `"interruption"` — the OS (incoming call, audio focus loss, headphones unplugged). `"notification"` — Android only, a button in the notification |

A restart caused by calling `start()` again emits nothing: you asked for it, and it is one transition from `running` to `running`.

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
| `PERMISSION_STATUSES`     | `["granted","denied","undetermined"]`                  | Valid notification permission statuses |

---

### Types

```ts
type BeatAccent = "strong" | "normal" | "muted";

type BeatEventPayload = {
  beat: number;
  timestamp: number;
  accent: BeatAccent;
};

type PlaybackState = "running" | "paused" | "stopped";

type PlaybackChangeReason = "explicit" | "interruption" | "notification";

type PlaybackEventPayload = {
  state: PlaybackState;
  reason: PlaybackChangeReason;
};

type MetronomeState = {
  state: PlaybackState;
  bpm: number;
  notificationVisible: boolean;
};

type PermissionStatus = "granted" | "denied" | "undetermined";

type NotificationPermission = {
  status: PermissionStatus;
  canAskAgain: boolean;
};

type SoundPreset = "click" | "beep" | "woodblock" | "rim" | "hihat" | "cowbell";

type LockscreenVisibility = "public" | "private" | "secret";

type BackgroundOptions = {
  title?: string;
  text?: string;
  icon?: string;
  color?: string;
  stopLabel?: string;
  pauseLabel?: string;
  resumeLabel?: string;
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

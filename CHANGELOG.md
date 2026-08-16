<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# Changelog

## [2.0.0](https://github.com/guitar0-net/expo-precision-metronome/compare/v1.4.0...v2.0.0) (2026-08-16)


### ⚠ BREAKING CHANGES

* replace onStop with onPlaybackChange

### Features

* **android:** add a notification permission request api ([893f866](https://github.com/guitar0-net/expo-precision-metronome/commit/893f866f51ef9a32e4e673646bf281caed9e2866))
* **android:** add pause and resume actions to the notification ([48fab1a](https://github.com/guitar0-net/expo-precision-metronome/commit/48fab1a09a45fa24ec47a52269da0630917a2218))
* **android:** pause playback without tearing down the audio stream ([4c53b5a](https://github.com/guitar0-net/expo-precision-metronome/commit/4c53b5a7a2c9521e5f46aebb83edf70e7c42c36b))
* **android:** reject the permission request when the manifest lacks it ([04f1ca8](https://github.com/guitar0-net/expo-precision-metronome/commit/04f1ca866e1eacdfbbe696dd185cf9b5ddb74f78))
* **android:** show the bpm in the notification only while backgrounded ([0c7fe04](https://github.com/guitar0-net/expo-precision-metronome/commit/0c7fe043d379e2f97eb671e4faf44e211b3b6434))
* **ios:** pause playback while keeping the audio session active ([ada4bf0](https://github.com/guitar0-net/expo-precision-metronome/commit/ada4bf0541059316c462187a4fb1e65b5d2b4921))
* pause on interruption and auto-resume after transient loss ([c463eef](https://github.com/guitar0-net/expo-precision-metronome/commit/c463eef14efc5fb20da376d976514349e8e4f161))
* replace onStop with onPlaybackChange ([4301c61](https://github.com/guitar0-net/expo-precision-metronome/commit/4301c61388dbfc6aa5cd7e8a244dca6f06da833b))
* report whether the notification permission can be asked again ([2601cb4](https://github.com/guitar0-net/expo-precision-metronome/commit/2601cb40c6bdc9895023660e2a9e3ba4e14aebe4))


### Bug Fixes

* **example:** keep a failed permission prompt from aborting playback ([31fd5b6](https://github.com/guitar0-net/expo-precision-metronome/commit/31fd5b6367a336f6c5c40381952c0efd2d4b4a9a))
* **ios:** restart the bar when resuming after an interruption ([c806641](https://github.com/guitar0-net/expo-precision-metronome/commit/c806641c40150d9419b2f63ed65e4075ed77dc74))

## [1.4.0](https://github.com/guitar0-net/expo-precision-metronome/compare/v1.3.0...v1.4.0) (2026-08-13)


### Features

* **android:** add media playback foreground service for background audio ([85b31e9](https://github.com/guitar0-net/expo-precision-metronome/commit/85b31e9514555cec41036ccb33314b6994741134))
* **ios:** add background audio guard and mixWithOthers option ([a55614d](https://github.com/guitar0-net/expo-precision-metronome/commit/a55614dd03e7613f54f90477731cf6faf8188819))
* **js:** add start options for background playback and mixing ([7b79c9e](https://github.com/guitar0-net/expo-precision-metronome/commit/7b79c9eacc786d8463457b1ebc362bc0af970188))
* **plugin:** add opt-in config plugin for background audio ([cc05098](https://github.com/guitar0-net/expo-precision-metronome/commit/cc0509847a3b04f3abe0ac0c649923245d2c74e6))


### Bug Fixes

* **android:** make the service stop intent explicit for the notification ([aabd20b](https://github.com/guitar0-net/expo-precision-metronome/commit/aabd20b52975a33b4fc0870582a7c209af82e383))
* **android:** pin the notification launch intent to the app package ([7079301](https://github.com/guitar0-net/expo-precision-metronome/commit/7079301fe6587117c230526d9a02fe96bda215ee))
* **android:** promote the service to the foreground before bailing out ([42cc216](https://github.com/guitar0-net/expo-precision-metronome/commit/42cc216582b2fdfcd89568b3cc55a11a221fdef6))
* **android:** restart playback when start() runs on a live stream ([9125f7a](https://github.com/guitar0-net/expo-precision-metronome/commit/9125f7a22786ff8b5d508b13d46e6582605a76bc))
* **android:** verify foreground service permissions before background start ([47a8ebf](https://github.com/guitar0-net/expo-precision-metronome/commit/47a8ebf1d6364e2d4a387d676eceaddd3ca40e85))
* **ios:** restart the engine when start() runs on a live engine ([3595e3a](https://github.com/guitar0-net/expo-precision-metronome/commit/3595e3aba2b44211b843b2c4fed8beeac5d6d72d))
* **plugin:** resolve package metadata relative to the plugin bundle ([74f075c](https://github.com/guitar0-net/expo-precision-metronome/commit/74f075c23b0717ebb62557ff3258412a8209f32f))


### Performance Improvements

* **android:** refresh the notification in place instead of restarting the service ([35c149c](https://github.com/guitar0-net/expo-precision-metronome/commit/35c149cdceac92ec2a983b3ae9c5a8fa43462317))

## [1.3.0](https://github.com/guitar0-net/expo-precision-metronome/compare/v1.2.0...v1.3.0) (2026-08-09)


### Miscellaneous Chores

* release 1.3.0 ([bf64e49](https://github.com/guitar0-net/expo-precision-metronome/commit/bf64e498016aad4f413caa4b7f21519189982c48))

## [1.2.0](https://github.com/guitar0-net/expo-precision-metronome/compare/v1.1.0...v1.2.0) (2026-06-09)


### Features

* implement accent pattern API with setPattern and beat accent levels ([8cd11f0](https://github.com/guitar0-net/expo-precision-metronome/commit/8cd11f03a3eb81d2aa1202ca49de8e3d62bf4af2))

## [Unreleased]

### Breaking Changes

- `onBeat` event `beat` field is now **0-based** (was 1-based). Update any code that treats `beat === 1` as the downbeat to use `beat === 0` instead.

## [1.1.0](https://github.com/guitar0-net/expo-precision-metronome/compare/v1.0.0...v1.1.0) (2026-06-07)

### Features

- add songs variety ([b505ad8](https://github.com/guitar0-net/expo-precision-metronome/commit/b505ad8e8f48cc38a945a1bc0e122060edbd48a1))

## [1.0.0](https://github.com/guitar0-net/expo-precision-metronome/releases/tag/v1.0.0) (2026-05-23)

### Features

- Public TypeScript API: `start(bpm)`, `stop()`, `setBpm(bpm)`, `onBeat` event, `onStop` event
- `BPM_MIN` / `BPM_MAX` constants (20–300)
- Full TypeScript types for all events and constants
- JSI-based native module wiring (no JSON bridge)
- Expo Modules API scaffold for iOS (Swift) and Android (Kotlin)
- CI pipeline: JS lint + type-check + tests, Android ktlint + lint + build (API 34 & 35), iOS SwiftLint + Swift tests + simulator build, REUSE compliance, CodeQL

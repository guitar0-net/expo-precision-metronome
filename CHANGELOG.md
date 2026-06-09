<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# Changelog

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

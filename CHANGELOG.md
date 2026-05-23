<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# Changelog

## [1.0.0](https://github.com/guitar0-net/expo-precision-metronome/releases/tag/v1.0.0) (2026-05-23)

### Features

- Public TypeScript API: `start(bpm)`, `stop()`, `setBpm(bpm)`, `onBeat` event, `onStop` event
- `BPM_MIN` / `BPM_MAX` constants (20–300)
- Full TypeScript types for all events and constants
- JSI-based native module wiring (no JSON bridge)
- Expo Modules API scaffold for iOS (Swift) and Android (Kotlin)
- CI pipeline: JS lint + type-check + tests, Android ktlint + lint + build (API 34 & 35), iOS SwiftLint + Swift tests + simulator build, REUSE compliance, CodeQL

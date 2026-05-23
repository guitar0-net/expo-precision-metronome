<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# Contributing

## Prerequisites

- Node 18+
- Xcode 16+ (iOS)
- Android Studio with API 26+ SDK (Android)
- CocoaPods (`gem install cocoapods`)

## Setup

```sh
# Install root dependencies
npm install

# Install example app dependencies
cd example && npm install

# iOS — install pods
cd example/ios && pod install
```

## Running the example app

```sh
cd example
npx expo run:ios       # iOS simulator
npx expo run:android   # Android emulator / device
```

## Running checks

```sh
# JS: lint + type-check + tests
npm run check

# Swift unit tests
npm run test:swift

# Android unit tests
npm run test:android

# Kotlin lint
npm run lint:kt

# Swift lint
npm run lint:swift
```

## Commit convention

This project uses [Conventional Commits](https://www.conventionalcommits.org/). All commit messages must follow the format:

```
<type>(optional scope): <description>

feat:     new feature
fix:      bug fix
chore:    tooling, CI, deps
docs:     documentation only
refactor: code change with no feature or fix
test:     tests only
```

A `commit-msg` hook enforces this automatically via commitlint.

## Pull requests

1. Fork the repo and create a branch from `main`.
2. Make your changes and ensure `npm run check` passes.
3. Open a PR — the CI will run lint, type-check, tests, and native builds.
4. A maintainer will review and merge.

## Code of Conduct

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before contributing.

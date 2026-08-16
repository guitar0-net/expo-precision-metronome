<!--
SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>

SPDX-License-Identifier: MIT
-->

# Migration guide

## 1.x → 2.0.0

2.0 adds a real `paused` state, and the Android notification gains the buttons to drive it. Playback commands can now originate outside JS, which is what forces the breaking part: a single event carrying the whole state, instead of one event per ending.

Everything below is the complete set of changes. There is nothing to do beyond them — `start()`, `setBpm()`, `setSound()`, `setPattern()` and the config plugin are unchanged.

### `onStop` is replaced by `onPlaybackChange`

`onStop` and `StopEventPayload` are **removed**, not deprecated.

```diff
-const sub = ExpoPrecisionMetronomeModule.addListener("onStop", ({ reason }) => {
-  setPlaying(false);
-  console.log(`Stopped: ${reason}`);
-});
+const sub = ExpoPrecisionMetronomeModule.addListener(
+  "onPlaybackChange",
+  ({ state, reason }) => {
+    setPlayback(state); // "running" | "paused" | "stopped"
+    console.log(`Playback ${state}: ${reason}`);
+  },
+);
```

The literal replacement for the old handler is a check on `state === "stopped"`, but folding the event into one state variable is the point: three independent streams (`onStop` / `onPause` / `onResume`) would have made every consumer write a reducer, and any missed case desynchronises the UI. One event carrying `state` makes that impossible.

`reason` keeps its old values and meanings, and now describes pauses and resumes too.

### Interruptions pause instead of stopping

Previously every non-explicit cause collapsed into a stop. 2.0 follows platform convention:

| Cause                                              | 1.x  | 2.0                                                        |
| -------------------------------------------------- | ---- | ---------------------------------------------------------- |
| Headphones unplugged (Android)                     | stop | **pause**, never auto-resumed                              |
| Incoming call / transient audio focus loss         | stop | **pause**, auto-resumed when the interruption ends cleanly |
| Another app takes over audio permanently (Android) | stop | stop                                                       |
| iOS interruption ends without `.shouldResume`      | —    | stays paused                                               |

If your UI treated the old `onStop` as "the session is over", it now needs to distinguish `paused` from `stopped` — otherwise a phone call leaves your UI showing a stopped metronome that is in fact still alive and one `resume()` away.

### `beat` counts from `resume()` too

`resume()` re-enters on the downbeat, so the beat counter resets. This is a musical requirement: pause is pressed by ear at an arbitrary moment, and a musician re-enters on "one".

A bar number derived from `beat` — `Math.floor(beat / 4)` — restarts with it. That is correct, the bar really does start over, but a consumer that assumed `beat` only ever increases needs to know.

### Reconcile on mount with `getState()`

New, and worth adopting even if nothing else in your app changes. Events only cover transitions your listener was mounted for. With pause reachable from the notification, this sequence is now ordinary:

1. the user backgrounds your app,
2. pauses from the notification shade,
3. comes back to a remounted React tree,
4. your UI claims the metronome is playing.

```ts
useEffect(() => {
  getState().then(({ state, bpm }) => {
    setPlayback(state);
    if (state !== "stopped") setBpm(bpm);
  });
}, []);
```

### The notification shows the tempo only while backgrounded

The default `"{bpm} BPM"` line is omitted while your app is on screen, because the user is reading the tempo from your own UI at that moment and two numbers can disagree. It comes back as soon as the app is backgrounded.

Nothing to do unless you relied on the notification always carrying the tempo. If you need a line that is always present, set `background.text` — a value you supply is static and is shown in both modes.

### Ask for `POST_NOTIFICATIONS` through the library

The notification is now the only way to pause from outside the app, so a missing permission costs a feature rather than an indicator.

```diff
-import { PermissionsAndroid, Platform } from "react-native";
-
-if (Platform.OS === "android" && Number(Platform.Version) >= 33) {
-  await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
-}
+import {
+  getNotificationPermission,
+  requestNotificationPermission,
+} from "expo-precision-metronome";
+
+const { status, canAskAgain } = await getNotificationPermission();
+if (status !== "granted" && canAskAgain) {
+  await requestNotificationPermission();
+}
```

Gate on `canAskAgain`, not on `status === "undetermined"`. Android 13 shows the dialog again after the first denial and only stops after the second, so a check for `"undetermined"` throws away the one retry the platform allows — the retry that gets the feature back for a user who declined by reflex.

Both resolve as granted on iOS and below Android 13, so no platform branch is needed. The library still never prompts by itself — when to ask remains your decision. `start({ background })` does not throw when the permission is missing; playback works, and `getState().notificationVisible` reports that the notification is hidden.

`requestNotificationPermission()` rejects with `ERR_NO_FOREGROUND_ACTIVITY` when no activity is on screen, because Android cannot show the dialog then. Call it from a screen the user is looking at.

### `showStopButton: false` no longer means "no buttons"

The notification always renders a pause button now, so that flag turns the notification from **Pause + Stop** into **Pause alone**, where in 1.x it produced a notification with no buttons at all.

There is no flag that brings the button-free notification back. Suspending playback from outside the app is what the notification is for since 2.0, and a notification that cannot do it is a status indicator the user cannot act on.

### New, additive

- `pause()` and `resume()` — idempotent, and deliberately not a toggle. See the [README](README.md#pausing).
- `background.pauseLabel` / `background.resumeLabel` — Android button labels, defaulting to `"Pause"` and `"Resume"`.

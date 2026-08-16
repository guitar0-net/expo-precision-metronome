export const BPM_MIN = 20;
export const BPM_MAX = 300;

export const SOUND_PRESETS = [
  "click",
  "beep",
  "woodblock",
  "rim",
  "hihat",
  "cowbell",
] as const;
export type SoundPreset = (typeof SOUND_PRESETS)[number];

export const BEAT_ACCENTS = ["strong", "normal", "muted"] as const;
export type BeatAccent = (typeof BEAT_ACCENTS)[number];
export const BEAT_PATTERN_MAX_LENGTH = 16;
export const DEFAULT_BEAT_PATTERN: readonly BeatAccent[] = [
  "strong",
  "normal",
  "normal",
  "normal",
];

export type BeatEventPayload = {
  /**
   * Counts from `start()` **or** `resume()` — resuming re-enters on the downbeat,
   * so a bar number derived from this (`Math.floor(beat / 4)`) restarts too.
   */
  beat: number;
  timestamp: number;
  accent: BeatAccent;
};

export const LOCKSCREEN_VISIBILITIES = ["public", "private", "secret"] as const;
export type LockscreenVisibility = (typeof LOCKSCREEN_VISIBILITIES)[number];

/**
 * Notification shown while the metronome runs in the background. Everything but
 * `title` and `text` is Android-only — iOS has no equivalent UI and ignores it.
 */
export type BackgroundOptions = {
  /** @default "Metronome" */
  title?: string;
  /** Defaults to `"{bpm} BPM"`, which follows `setBpm()`. */
  text?: string;
  /** Android: drawable or mipmap resource name. Defaults to the app icon. */
  icon?: string;
  /** Android: accent colour, e.g. `"#f59e0b"`. */
  color?: string;
  /** Android: label of the stop button. @default "Stop" */
  stopLabel?: string;
  /** Android: label of the pause button. @default "Pause" */
  pauseLabel?: string;
  /** Android: label of the resume button. @default "Resume" */
  resumeLabel?: string;
  /** Android: @default true */
  showStopButton?: boolean;
  /** Android: channel name shown in system settings. @default "Metronome" */
  channelName?: string;
  /** Android: @default "public" */
  lockscreenVisibility?: LockscreenVisibility;
};

export type StartOptions = {
  /**
   * Keep playing while the app is backgrounded. `true` uses the defaults.
   *
   * Requires the config plugin — `["expo-precision-metronome", { "backgroundAudio": true }]`
   * — otherwise `start()` rejects with `ERR_BACKGROUND_NOT_CONFIGURED`.
   */
  background?: boolean | BackgroundOptions;
  /**
   * Let audio from other apps keep playing instead of interrupting it — the usual
   * choice when practising over a backing track. Applies from the next `start()`.
   *
   * @default false
   */
  mixWithOthers?: boolean;
};

/** What the native module actually receives: `background` is normalised to a record. */
export type NativeStartOptions = {
  background?: BackgroundOptions;
  mixWithOthers?: boolean;
};

/** `paused` keeps the audio engine alive and silences it; `stopped` tears it down. */
export type PlaybackState = "running" | "paused" | "stopped";

/** `"notification"` is Android-only — the user pressed a button in the notification. */
export type PlaybackChangeReason = "explicit" | "notification" | "interruption";

export type PlaybackEventPayload = {
  state: PlaybackState;
  reason: PlaybackChangeReason;
};

export type MetronomeState = {
  state: PlaybackState;
  bpm: number;
  /**
   * Android: `false` when background playback was requested but POST_NOTIFICATIONS
   * was not granted — the notification is hidden, so pause is only reachable from
   * inside the app. Always `false` on iOS.
   */
  notificationVisible: boolean;
};

export const PERMISSION_STATUSES = ["granted", "denied", "undetermined"] as const;
/**
 * Android 13+ runtime status of `POST_NOTIFICATIONS`. `"undetermined"` means the
 * dialog has not been shown yet, so a request would surface it; `"denied"` means it
 * was declined and, after the second denial, will never appear again.
 *
 * Always `"granted"` on iOS and below Android 13, where posting the notification
 * needs no runtime permission. Whether one is actually visible is a separate
 * question — see `MetronomeState.notificationVisible`.
 */
export type PermissionStatus = (typeof PERMISSION_STATUSES)[number];

export type ExpoPrecisionMetronomeModuleEvents = {
  onBeat: (params: BeatEventPayload) => void;
  onPlaybackChange: (params: PlaybackEventPayload) => void;
};

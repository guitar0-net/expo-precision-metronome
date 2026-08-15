import {
  BEAT_ACCENTS,
  BEAT_PATTERN_MAX_LENGTH,
  BPM_MAX,
  BPM_MIN,
  LOCKSCREEN_VISIBILITIES,
  SOUND_PRESETS,
} from "./ExpoPrecisionMetronome.types";
import type {
  BackgroundOptions,
  BeatAccent,
  MetronomeState,
  NativeStartOptions,
  SoundPreset,
  StartOptions,
} from "./ExpoPrecisionMetronome.types";
import ExpoPrecisionMetronomeModule from "./ExpoPrecisionMetronomeModule";

export * from "./ExpoPrecisionMetronome.types";

const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

const BACKGROUND_STRING_FIELDS = [
  "title",
  "text",
  "icon",
  "stopLabel",
  "pauseLabel",
  "resumeLabel",
  "channelName",
] as const;

function assertBpm(bpm: number): void {
  if (!Number.isFinite(bpm) || bpm < BPM_MIN || bpm > BPM_MAX) {
    throw new RangeError(`BPM must be between ${BPM_MIN} and ${BPM_MAX}, got ${bpm}`);
  }
}

function assertBackgroundOptions(options: BackgroundOptions): void {
  for (const field of BACKGROUND_STRING_FIELDS) {
    const value = options[field];
    if (value !== undefined && typeof value !== "string") {
      throw new TypeError(`background.${field} must be a string, got ${typeof value}`);
    }
  }

  if (
    options.showStopButton !== undefined &&
    typeof options.showStopButton !== "boolean"
  ) {
    throw new TypeError(
      `background.showStopButton must be a boolean, got ${typeof options.showStopButton}`,
    );
  }

  if (options.color !== undefined && !HEX_COLOR.test(options.color)) {
    throw new TypeError(
      `background.color must be a hex colour like "#f59e0b", got "${options.color}"`,
    );
  }

  if (
    options.lockscreenVisibility !== undefined &&
    !(LOCKSCREEN_VISIBILITIES as readonly string[]).includes(
      options.lockscreenVisibility,
    )
  ) {
    throw new TypeError(
      `background.lockscreenVisibility must be one of: ${LOCKSCREEN_VISIBILITIES.join(
        ", ",
      )}, got "${options.lockscreenVisibility}"`,
    );
  }
}

/**
 * Collapses `background: true | false | {…}` into the record the native modules
 * expect, where presence alone means "enabled".
 */
function toNativeOptions(options: StartOptions): NativeStartOptions {
  if (
    options.mixWithOthers !== undefined &&
    typeof options.mixWithOthers !== "boolean"
  ) {
    throw new TypeError(
      `mixWithOthers must be a boolean, got ${typeof options.mixWithOthers}`,
    );
  }

  const native: NativeStartOptions = { mixWithOthers: options.mixWithOthers ?? false };
  const { background } = options;

  if (background === undefined || background === false) {
    return native;
  }

  if (background === true) {
    native.background = {};
    return native;
  }

  if (typeof background !== "object" || background === null) {
    throw new TypeError(
      `background must be a boolean or an object, got ${typeof background}`,
    );
  }

  assertBackgroundOptions(background);
  native.background = background;
  return native;
}

/**
 * Starts the metronome at `bpm`.
 *
 * Restarts the engine when it is already running or paused, so the new tempo and
 * options always take effect. The restart emits no `onPlaybackChange` — use
 * `setBpm()` to change tempo without the audible gap.
 */
export async function start(bpm: number, options?: StartOptions): Promise<void> {
  assertBpm(bpm);
  if (options === undefined) {
    return ExpoPrecisionMetronomeModule.start(bpm);
  }
  if (typeof options !== "object" || options === null) {
    throw new TypeError(`options must be an object, got ${typeof options}`);
  }
  return ExpoPrecisionMetronomeModule.start(bpm, toNativeOptions(options));
}

export function stop(): Promise<void> {
  return ExpoPrecisionMetronomeModule.stop();
}

/**
 * Silences the metronome while keeping the audio engine alive, so `resume()` is
 * immediate and sample-accurate.
 *
 * Idempotent: a no-op unless playback is running, and a no-op emits no
 * `onPlaybackChange`. The notification and the app's own UI are independent
 * command sources, so a lost race must stay harmless — that is why this is not a
 * toggle.
 */
export function pause(): Promise<void> {
  return ExpoPrecisionMetronomeModule.pause();
}

/**
 * Resumes a paused metronome on the downbeat — the beat counter resets, because
 * pause is pressed by ear at an arbitrary moment and a musician re-enters on "one".
 *
 * Idempotent: a no-op unless playback is paused. In particular it does not throw
 * when playback has already stopped.
 */
export function resume(): Promise<void> {
  return ExpoPrecisionMetronomeModule.resume();
}

/**
 * Reads the current playback state.
 *
 * `onPlaybackChange` covers transitions, not a listener that was not mounted for
 * one: with playback controllable from the notification, an app that remounted
 * while backgrounded has no other way to find out what happened. Call it on mount
 * to reconcile.
 */
export function getState(): Promise<MetronomeState> {
  return ExpoPrecisionMetronomeModule.getState();
}

export async function setBpm(bpm: number): Promise<void> {
  assertBpm(bpm);
  return ExpoPrecisionMetronomeModule.setBpm(bpm);
}

export async function setSound(sound: SoundPreset): Promise<void> {
  if (!(SOUND_PRESETS as readonly string[]).includes(sound)) {
    throw new TypeError(
      `sound must be one of: ${SOUND_PRESETS.join(", ")}, got "${sound}"`,
    );
  }
  return ExpoPrecisionMetronomeModule.setSound(sound);
}

export async function setPattern(pattern: BeatAccent[]): Promise<void> {
  if (
    !Array.isArray(pattern) ||
    pattern.length < 1 ||
    pattern.length > BEAT_PATTERN_MAX_LENGTH
  ) {
    throw new RangeError(
      `pattern must have 1–${BEAT_PATTERN_MAX_LENGTH} elements, got ${
        Array.isArray(pattern) ? pattern.length : typeof pattern
      }`,
    );
  }
  for (const accent of pattern) {
    if (!(BEAT_ACCENTS as readonly string[]).includes(accent)) {
      throw new TypeError(
        `each accent must be one of: ${BEAT_ACCENTS.join(", ")}, got "${accent}"`,
      );
    }
  }
  return ExpoPrecisionMetronomeModule.setPattern(pattern);
}

export { default as ExpoPrecisionMetronomeView } from "./ExpoPrecisionMetronomeView";
export type { ExpoPrecisionMetronomeViewProps } from "./ExpoPrecisionMetronomeView";

export default ExpoPrecisionMetronomeModule;

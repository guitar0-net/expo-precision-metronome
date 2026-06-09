import {
  BEAT_ACCENTS,
  BEAT_PATTERN_MAX_LENGTH,
  BeatAccent,
  BPM_MAX,
  BPM_MIN,
  SOUND_PRESETS,
  SoundPreset,
} from "./ExpoPrecisionMetronome.types";
import ExpoPrecisionMetronomeModule from "./ExpoPrecisionMetronomeModule";

export * from "./ExpoPrecisionMetronome.types";

function assertBpm(bpm: number): void {
  if (!Number.isFinite(bpm) || bpm < BPM_MIN || bpm > BPM_MAX) {
    throw new RangeError(`BPM must be between ${BPM_MIN} and ${BPM_MAX}, got ${bpm}`);
  }
}

export async function start(bpm: number): Promise<void> {
  assertBpm(bpm);
  return ExpoPrecisionMetronomeModule.start(bpm);
}

export function stop(): Promise<void> {
  return ExpoPrecisionMetronomeModule.stop();
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

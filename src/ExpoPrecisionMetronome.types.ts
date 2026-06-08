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
  beat: number;
  timestamp: number;
  accent: BeatAccent;
};

export type StopEventPayload = {
  reason: "explicit" | "interruption";
};

export type ExpoPrecisionMetronomeModuleEvents = {
  onBeat: (params: BeatEventPayload) => void;
  onStop: (params: StopEventPayload) => void;
};

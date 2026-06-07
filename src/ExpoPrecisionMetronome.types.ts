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

export type BeatEventPayload = {
  beat: number;
  timestamp: number;
};

export type StopEventPayload = {
  reason: "explicit" | "interruption";
};

export type ExpoPrecisionMetronomeModuleEvents = {
  onBeat: (params: BeatEventPayload) => void;
  onStop: (params: StopEventPayload) => void;
};

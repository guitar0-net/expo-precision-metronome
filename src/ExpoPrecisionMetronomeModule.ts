import { NativeModule, requireNativeModule } from "expo";

import type {
  BeatAccent,
  ExpoPrecisionMetronomeModuleEvents,
  NativeStartOptions,
  SoundPreset,
} from "./ExpoPrecisionMetronome.types";

declare class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  start(bpm: number, options?: NativeStartOptions): Promise<void>;
  stop(): Promise<void>;
  setBpm(bpm: number): Promise<void>;
  setSound(sound: SoundPreset): Promise<void>;
  setPattern(pattern: BeatAccent[]): Promise<void>;
}

export default requireNativeModule<ExpoPrecisionMetronomeModule>(
  "ExpoPrecisionMetronome",
);

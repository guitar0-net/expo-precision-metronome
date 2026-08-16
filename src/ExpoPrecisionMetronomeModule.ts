import { NativeModule, requireNativeModule } from "expo";

import type {
  BeatAccent,
  ExpoPrecisionMetronomeModuleEvents,
  MetronomeState,
  NativeStartOptions,
  NotificationPermission,
  SoundPreset,
} from "./ExpoPrecisionMetronome.types";

declare class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  start(bpm: number, options?: NativeStartOptions): Promise<void>;
  stop(): Promise<void>;
  pause(): Promise<void>;
  resume(): Promise<void>;
  getState(): Promise<MetronomeState>;
  requestNotificationPermission(): Promise<boolean>;
  getNotificationPermission(): Promise<NotificationPermission>;
  setBpm(bpm: number): Promise<void>;
  setSound(sound: SoundPreset): Promise<void>;
  setPattern(pattern: BeatAccent[]): Promise<void>;
}

export default requireNativeModule<ExpoPrecisionMetronomeModule>(
  "ExpoPrecisionMetronome",
);

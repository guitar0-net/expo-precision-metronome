import { NativeModule, requireNativeModule } from "expo";

import { ExpoPrecisionMetronomeModuleEvents } from "./ExpoPrecisionMetronome.types";

declare class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  start(bpm: number): Promise<void>;
  stop(): Promise<void>;
  setBpm(bpm: number): Promise<void>;
}

export default requireNativeModule<ExpoPrecisionMetronomeModule>(
  "ExpoPrecisionMetronome",
);

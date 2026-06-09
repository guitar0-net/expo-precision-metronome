import { NativeModule, registerWebModule } from "expo";

import { ExpoPrecisionMetronomeModuleEvents } from "./ExpoPrecisionMetronome.types";

class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  async start(_bpm: number): Promise<void> {
    throw new Error("ExpoPrecisionMetronome is not supported on web");
  }
  async stop(): Promise<void> {
    throw new Error("ExpoPrecisionMetronome is not supported on web");
  }
  async setBpm(_bpm: number): Promise<void> {
    throw new Error("ExpoPrecisionMetronome is not supported on web");
  }
  async setSound(_sound: string): Promise<void> {
    throw new Error("ExpoPrecisionMetronome is not supported on web");
  }
  async setPattern(_pattern: string[]): Promise<void> {
    throw new Error("ExpoPrecisionMetronome is not supported on web");
  }
}

export default registerWebModule(
  ExpoPrecisionMetronomeModule,
  "ExpoPrecisionMetronomeModule",
);

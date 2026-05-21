import { NativeModule, requireNativeModule } from "expo";

import { ExpoPrecisionMetronomeModuleEvents } from "./ExpoPrecisionMetronome.types";

declare class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoPrecisionMetronomeModule>(
  "ExpoPrecisionMetronome",
);

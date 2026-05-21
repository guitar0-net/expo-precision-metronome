import { registerWebModule, NativeModule } from "expo";

import { ExpoPrecisionMetronomeModuleEvents } from "./ExpoPrecisionMetronome.types";

class ExpoPrecisionMetronomeModule extends NativeModule<ExpoPrecisionMetronomeModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit("onChange", { value });
  }
  hello() {
    return "Hello world! 👋";
  }
}

export default registerWebModule(
  ExpoPrecisionMetronomeModule,
  "ExpoPrecisionMetronomeModule",
);

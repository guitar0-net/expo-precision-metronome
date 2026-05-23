import { BPM_MAX, BPM_MIN } from "./ExpoPrecisionMetronome.types";
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

export { default as ExpoPrecisionMetronomeView } from "./ExpoPrecisionMetronomeView";
export type { ExpoPrecisionMetronomeViewProps } from "./ExpoPrecisionMetronomeView";

export default ExpoPrecisionMetronomeModule;

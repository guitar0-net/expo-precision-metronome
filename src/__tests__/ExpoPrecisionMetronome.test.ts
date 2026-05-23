import { beforeEach, describe, expect, jest, test } from "@jest/globals";

import { BPM_MAX, BPM_MIN } from "../ExpoPrecisionMetronome.types";
import ExpoPrecisionMetronomeModule from "../ExpoPrecisionMetronomeModule";
import { setBpm, start, stop } from "../index";

const mod = jest.mocked(ExpoPrecisionMetronomeModule);

beforeEach(() => {
  jest.clearAllMocks();
});

describe("start()", () => {
  test.each([BPM_MIN, 60, 120, BPM_MAX])(
    "delegates BPM %s to native module",
    async (bpm) => {
      await start(bpm);
      expect(mod.start).toHaveBeenCalledWith(bpm);
      expect(mod.start).toHaveBeenCalledTimes(1);
    },
  );

  test.each([BPM_MIN - 1, 0, -1, -Infinity, NaN, BPM_MAX + 1, 1000, Infinity])(
    "rejects with RangeError and does not call native module for %s",
    async (bpm) => {
      await expect(start(bpm)).rejects.toBeInstanceOf(RangeError);
      expect(mod.start).not.toHaveBeenCalled();
    },
  );

  test("error message includes bounds and actual value", async () => {
    await expect(start(BPM_MIN - 1)).rejects.toThrow(
      `BPM must be between ${BPM_MIN} and ${BPM_MAX}, got ${BPM_MIN - 1}`,
    );
  });
});

describe("stop()", () => {
  test("delegates to native module", async () => {
    await stop();
    expect(mod.stop).toHaveBeenCalledTimes(1);
  });

  test("can be called multiple times without error", async () => {
    await stop();
    await stop();
    expect(mod.stop).toHaveBeenCalledTimes(2);
  });
});

describe("setBpm()", () => {
  test.each([BPM_MIN, 60, 120, BPM_MAX])(
    "delegates BPM %s to native module",
    async (bpm) => {
      await setBpm(bpm);
      expect(mod.setBpm).toHaveBeenCalledWith(bpm);
      expect(mod.setBpm).toHaveBeenCalledTimes(1);
    },
  );

  test.each([BPM_MIN - 1, 0, -1, -Infinity, NaN, BPM_MAX + 1, 1000, Infinity])(
    "rejects with RangeError and does not call native module for %s",
    async (bpm) => {
      await expect(setBpm(bpm)).rejects.toBeInstanceOf(RangeError);
      expect(mod.setBpm).not.toHaveBeenCalled();
    },
  );

  test("error message includes bounds and actual value", async () => {
    await expect(setBpm(BPM_MAX + 1)).rejects.toThrow(
      `BPM must be between ${BPM_MIN} and ${BPM_MAX}, got ${BPM_MAX + 1}`,
    );
  });
});

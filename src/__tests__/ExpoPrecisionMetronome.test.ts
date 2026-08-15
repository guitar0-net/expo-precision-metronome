import { beforeEach, describe, expect, jest, test } from "@jest/globals";

import {
  BEAT_ACCENTS,
  BEAT_PATTERN_MAX_LENGTH,
  BeatAccent,
  BPM_MAX,
  BPM_MIN,
  ExpoPrecisionMetronomeModuleEvents,
  SOUND_PRESETS,
  SoundPreset,
} from "../ExpoPrecisionMetronome.types";
import ExpoPrecisionMetronomeModule from "../ExpoPrecisionMetronomeModule";
import {
  getState,
  pause,
  resume,
  setBpm,
  setPattern,
  setSound,
  start,
  stop,
} from "../index";

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

describe("start() options", () => {
  test("omits the options argument entirely when none are given", async () => {
    await start(120);
    expect(mod.start).toHaveBeenCalledWith(120);
  });

  test("expands background: true into an empty record", async () => {
    await start(120, { background: true });
    expect(mod.start).toHaveBeenCalledWith(120, {
      background: {},
      mixWithOthers: false,
    });
  });

  test("drops the background record when disabled", async () => {
    await start(120, { background: false, mixWithOthers: true });
    expect(mod.start).toHaveBeenCalledWith(120, { mixWithOthers: true });
  });

  test("passes notification options through", async () => {
    const background = {
      title: "Practice",
      text: "4/4",
      color: "#f59e0b",
      pauseLabel: "Hold",
      resumeLabel: "Carry on",
      showStopButton: false,
      lockscreenVisibility: "private" as const,
    };

    await start(120, { background });

    expect(mod.start).toHaveBeenCalledWith(120, { background, mixWithOthers: false });
  });

  test.each([
    ["title", { title: 1 }],
    ["text", { text: {} }],
    ["icon", { icon: false }],
    ["stopLabel", { stopLabel: null }],
    ["pauseLabel", { pauseLabel: 3 }],
    ["resumeLabel", { resumeLabel: [] }],
    ["channelName", { channelName: 7 }],
    ["showStopButton", { showStopButton: "yes" }],
    ["color", { color: "orange" }],
    ["lockscreenVisibility", { lockscreenVisibility: "hidden" }],
  ])("rejects invalid background.%s", async (_field, background) => {
    await expect(start(120, { background } as never)).rejects.toBeInstanceOf(TypeError);
    expect(mod.start).not.toHaveBeenCalled();
  });

  test.each([
    ["a string", "yes"],
    ["a number", 1],
    ["null", null],
  ])("rejects background given as %s", async (_label, background) => {
    await expect(start(120, { background } as never)).rejects.toBeInstanceOf(TypeError);
    expect(mod.start).not.toHaveBeenCalled();
  });

  test("rejects a non-boolean mixWithOthers", async () => {
    await expect(start(120, { mixWithOthers: "yes" } as never)).rejects.toBeInstanceOf(
      TypeError,
    );
    expect(mod.start).not.toHaveBeenCalled();
  });

  test("validates BPM before touching the options", async () => {
    await expect(start(BPM_MAX + 1, { background: true })).rejects.toBeInstanceOf(
      RangeError,
    );
    expect(mod.start).not.toHaveBeenCalled();
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

describe("pause() and resume()", () => {
  test("delegate to the native module", async () => {
    await pause();
    await resume();
    expect(mod.pause).toHaveBeenCalledTimes(1);
    expect(mod.resume).toHaveBeenCalledTimes(1);
  });

  // The notification and the app's own UI are independent command sources, so a
  // repeated command has to be harmless rather than an error.
  test("are idempotent from the JS side — no state is tracked here", async () => {
    await pause();
    await pause();
    await resume();
    await resume();
    expect(mod.pause).toHaveBeenCalledTimes(2);
    expect(mod.resume).toHaveBeenCalledTimes(2);
  });

  test("take no arguments, so neither can be turned into a toggle by a caller", () => {
    expect(pause.length).toBe(0);
    expect(resume.length).toBe(0);
  });
});

describe("getState()", () => {
  test("returns the native snapshot unchanged", async () => {
    const snapshot = {
      state: "paused" as const,
      bpm: 132,
      notificationVisible: true,
    };
    mod.getState.mockResolvedValue(snapshot);

    await expect(getState()).resolves.toEqual(snapshot);
    expect(mod.getState).toHaveBeenCalledTimes(1);
  });
});

describe("the event contract", () => {
  // onStop is removed in 2.0, not deprecated: one event carrying `state` cannot be
  // folded into a desynchronised UI the way three independent streams can. The
  // mapped type is what enforces it — the compiler rejects a missing or extra key,
  // so `tsc --noEmit` fails if onStop ever comes back.
  test("is exactly onBeat and onPlaybackChange", () => {
    const events: { [K in keyof ExpoPrecisionMetronomeModuleEvents]: true } = {
      onBeat: true,
      onPlaybackChange: true,
    };
    expect(Object.keys(events).sort()).toEqual(["onBeat", "onPlaybackChange"]);
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

describe("SOUND_PRESETS", () => {
  test("contains exactly the six expected presets", () => {
    expect(SOUND_PRESETS).toEqual([
      "click",
      "beep",
      "woodblock",
      "rim",
      "hihat",
      "cowbell",
    ]);
  });

  test("is readonly and non-empty", () => {
    expect(SOUND_PRESETS.length).toBeGreaterThan(0);
  });
});

describe("setSound()", () => {
  test.each(SOUND_PRESETS)("delegates preset '%s' to native module", async (preset) => {
    await setSound(preset);
    expect(mod.setSound).toHaveBeenCalledWith(preset);
    expect(mod.setSound).toHaveBeenCalledTimes(1);
  });

  test.each(["tick", "", "CLICK", "Click", 0, null, undefined] as unknown[])(
    "rejects with TypeError for invalid value %s",
    async (bad) => {
      await expect(setSound(bad as SoundPreset)).rejects.toBeInstanceOf(TypeError);
      expect(mod.setSound).not.toHaveBeenCalled();
    },
  );

  test("error message lists valid presets and the invalid value", async () => {
    await expect(setSound("tick" as SoundPreset)).rejects.toThrow(
      `sound must be one of: ${SOUND_PRESETS.join(", ")}, got "tick"`,
    );
  });
});

describe("BEAT_ACCENTS", () => {
  test("contains exactly the three expected accent levels", () => {
    expect(BEAT_ACCENTS).toEqual(["strong", "normal", "muted"]);
  });
});

describe("setPattern()", () => {
  test.each([
    [["strong"]],
    [["strong", "normal", "normal", "normal"]],
    [["strong", "muted", "normal"]],
    [Array(BEAT_PATTERN_MAX_LENGTH).fill("normal") as BeatAccent[]],
  ])("delegates valid pattern %j to native module", async (pattern) => {
    await setPattern(pattern);
    expect(mod.setPattern).toHaveBeenCalledWith(pattern);
    expect(mod.setPattern).toHaveBeenCalledTimes(1);
  });

  test.each([[[]], [Array(BEAT_PATTERN_MAX_LENGTH + 1).fill("normal")]])(
    "rejects with RangeError for pattern with invalid length %j",
    async (pattern) => {
      await expect(setPattern(pattern as BeatAccent[])).rejects.toBeInstanceOf(
        RangeError,
      );
      expect(mod.setPattern).not.toHaveBeenCalled();
    },
  );

  test.each([
    [["strong", "STRONG"] as unknown as BeatAccent[]],
    [["strong", "loud"] as unknown as BeatAccent[]],
    [["strong", ""] as unknown as BeatAccent[]],
    [["strong", null] as unknown as BeatAccent[]],
  ])(
    "rejects with TypeError when pattern contains invalid accent %j",
    async (pattern) => {
      await expect(setPattern(pattern)).rejects.toBeInstanceOf(TypeError);
      expect(mod.setPattern).not.toHaveBeenCalled();
    },
  );

  test("error message includes valid accents and the bad value", async () => {
    await expect(
      setPattern(["strong", "loud"] as unknown as BeatAccent[]),
    ).rejects.toThrow(
      `each accent must be one of: ${BEAT_ACCENTS.join(", ")}, got "loud"`,
    );
  });

  test("range error message includes max length and actual count", async () => {
    await expect(setPattern([])).rejects.toThrow(
      `pattern must have 1–${BEAT_PATTERN_MAX_LENGTH} elements, got 0`,
    );
  });

  test("rejects non-array argument", async () => {
    await expect(
      setPattern("strong" as unknown as BeatAccent[]),
    ).rejects.toBeInstanceOf(RangeError);
    expect(mod.setPattern).not.toHaveBeenCalled();
  });
});

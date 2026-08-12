import { createRunOncePlugin } from "expo/config-plugins";
import type { ConfigPlugin } from "expo/config-plugins";

import { withBackgroundAudioAndroid } from "./withBackgroundAudioAndroid";
import { withBackgroundAudioIos } from "./withBackgroundAudioIos";

const pkg = require("expo-precision-metronome/package.json");

export type MetronomePluginProps = {
  /**
   * Declare the platform entitlements required to keep the metronome running
   * while the app is backgrounded: a `mediaPlayback` foreground service on
   * Android and the `audio` background mode on iOS.
   *
   * Off by default — enabling it adds permissions that show up in the app's
   * store listing, so it is opt-in. Passing `background` to `start()` without
   * it throws `ERR_BACKGROUND_NOT_CONFIGURED`.
   *
   * @default false
   */
  backgroundAudio?: boolean;
};

const withExpoPrecisionMetronome: ConfigPlugin<MetronomePluginProps | void> = (
  config,
  props,
) => {
  if (!props?.backgroundAudio) {
    return config;
  }

  return withBackgroundAudioIos(withBackgroundAudioAndroid(config));
};

export default createRunOncePlugin(withExpoPrecisionMetronome, pkg.name, pkg.version);

import { withInfoPlist } from "expo/config-plugins";
import type { ConfigPlugin, InfoPlist } from "expo/config-plugins";

/**
 * Without this background mode iOS suspends the app a few seconds after it
 * leaves the foreground, which stops the audio render callback.
 */
export const AUDIO_BACKGROUND_MODE = "audio";

/** Idempotent — safe to run on an already-modified Info.plist. */
export function addAudioBackgroundMode(infoPlist: InfoPlist): InfoPlist {
  const modes = Array.isArray(infoPlist.UIBackgroundModes)
    ? (infoPlist.UIBackgroundModes as string[])
    : [];

  if (!modes.includes(AUDIO_BACKGROUND_MODE)) {
    modes.push(AUDIO_BACKGROUND_MODE);
  }

  infoPlist.UIBackgroundModes = modes;
  return infoPlist;
}

export const withBackgroundAudioIos: ConfigPlugin = (config) =>
  withInfoPlist(config, (modConfig) => {
    modConfig.modResults = addAudioBackgroundMode(modConfig.modResults);
    return modConfig;
  });

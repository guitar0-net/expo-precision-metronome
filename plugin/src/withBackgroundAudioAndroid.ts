import { AndroidConfig, withAndroidManifest } from "expo/config-plugins";
import type { ConfigPlugin } from "expo/config-plugins";

type AndroidManifest = AndroidConfig.Manifest.AndroidManifest;
type ManifestService = NonNullable<
  AndroidConfig.Manifest.ManifestApplication["service"]
>[number];

/** Fully qualified name of the foreground service shipped in the library AAR. */
export const SERVICE_NAME = "net.guitar0.metronome.MetronomeService";

/**
 * `POST_NOTIFICATIONS` is requested so the ongoing notification is visible on
 * Android 13+. Denying it does not stop the service — the notification is just
 * hidden from the shade.
 */
export const PERMISSIONS = [
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
  "android.permission.POST_NOTIFICATIONS",
];

/** Declares the foreground service. Idempotent — safe to run on an already-modified manifest. */
export function addMetronomeService(manifest: AndroidManifest): AndroidManifest {
  const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);
  const services = application.service ?? [];

  if (!services.some((service) => service.$["android:name"] === SERVICE_NAME)) {
    const service: ManifestService = {
      $: {
        "android:name": SERVICE_NAME,
        "android:exported": "false",
        "android:foregroundServiceType": "mediaPlayback",
      },
    };
    services.push(service);
  }

  application.service = services;
  return manifest;
}

export const withBackgroundAudioAndroid: ConfigPlugin = (config) => {
  const withPermissions = AndroidConfig.Permissions.withPermissions(
    config,
    PERMISSIONS,
  );

  return withAndroidManifest(withPermissions, (modConfig) => {
    modConfig.modResults = addMetronomeService(modConfig.modResults);
    return modConfig;
  });
};

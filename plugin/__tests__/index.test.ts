import type { ExpoConfig } from "expo/config";
import { AndroidConfig } from "expo/config-plugins";

import withExpoPrecisionMetronome from "../src";
import {
  addMetronomeService,
  PERMISSIONS,
  SERVICE_NAME,
  withBackgroundAudioAndroid,
} from "../src/withBackgroundAudioAndroid";
import {
  addAudioBackgroundMode,
  AUDIO_BACKGROUND_MODE,
} from "../src/withBackgroundAudioIos";

type AndroidManifest = AndroidConfig.Manifest.AndroidManifest;

function makeManifest(): AndroidManifest {
  return {
    manifest: {
      $: { "xmlns:android": "http://schemas.android.com/apk/res/android" },
      application: [{ $: { "android:name": ".MainApplication" } }],
    },
  } as AndroidManifest;
}

function makeConfig(): ExpoConfig {
  return { name: "test", slug: "test" };
}

describe("addMetronomeService", () => {
  it("declares the service with the mediaPlayback foreground type", () => {
    const manifest = addMetronomeService(makeManifest());
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);

    expect(application.service).toEqual([
      {
        $: {
          "android:name": SERVICE_NAME,
          "android:exported": "false",
          "android:foregroundServiceType": "mediaPlayback",
        },
      },
    ]);
  });

  it("does not duplicate the service when applied twice", () => {
    const manifest = addMetronomeService(addMetronomeService(makeManifest()));
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);

    expect(application.service).toHaveLength(1);
  });

  it("keeps services declared by the app", () => {
    const manifest = makeManifest();
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);
    application.service = [{ $: { "android:name": "com.example.OtherService" } }];

    const names = AndroidConfig.Manifest.getMainApplicationOrThrow(
      addMetronomeService(manifest),
    ).service?.map((service) => service.$["android:name"]);

    expect(names).toEqual(["com.example.OtherService", SERVICE_NAME]);
  });
});

describe("addAudioBackgroundMode", () => {
  it("adds the audio background mode to an empty plist", () => {
    expect(addAudioBackgroundMode({}).UIBackgroundModes).toEqual([
      AUDIO_BACKGROUND_MODE,
    ]);
  });

  it("does not duplicate the mode when applied twice", () => {
    const plist = addAudioBackgroundMode(addAudioBackgroundMode({}));

    expect(plist.UIBackgroundModes).toEqual([AUDIO_BACKGROUND_MODE]);
  });

  it("preserves background modes declared by the app", () => {
    const plist = addAudioBackgroundMode({ UIBackgroundModes: ["location"] });

    expect(plist.UIBackgroundModes).toEqual(["location", AUDIO_BACKGROUND_MODE]);
  });
});

describe("withBackgroundAudioAndroid", () => {
  it("adds the foreground service permissions", () => {
    const config = withBackgroundAudioAndroid(makeConfig());

    expect(config.android?.permissions).toEqual(expect.arrayContaining(PERMISSIONS));
  });
});

describe("withExpoPrecisionMetronome", () => {
  it("is a no-op without the backgroundAudio flag", () => {
    const config = withExpoPrecisionMetronome(makeConfig(), {});

    expect(config.android?.permissions).toBeUndefined();
    expect(config.mods).toBeUndefined();
  });

  it("registers android and ios mods when backgroundAudio is enabled", () => {
    const config = withExpoPrecisionMetronome(makeConfig(), {
      backgroundAudio: true,
    });

    expect(config.mods?.android?.manifest).toBeDefined();
    expect(config.mods?.ios?.infoPlist).toBeDefined();
  });
});

import ExpoModulesCore

extension SoundPreset: Enumerable {}
extension BeatAccent: Enumerable {}

private let bpmMin: Double = 20
private let bpmMax: Double = 300

private let audioBackgroundMode = "audio"

/// The notification fields this record carries are Android-only. On iOS only its
/// presence matters: it signals that the caller wants playback to survive
/// backgrounding, which requires the `audio` entry in `UIBackgroundModes`.
struct BackgroundOptions: Record {}

struct StartOptions: Record {
    @Field var background: BackgroundOptions?
    @Field var mixWithOthers: Bool = false
}

final class BackgroundNotConfiguredException: Exception {
    override var reason: String {
        "Background playback requires the 'audio' entry in UIBackgroundModes. "
            + "Add the config plugin to your app config: "
            + "[\"expo-precision-metronome\", { \"backgroundAudio\": true }]"
    }
}

/// Reading the merged Info.plist is the only reliable way to tell whether the
/// config plugin was applied — the entitlement lives in the app bundle, not here.
private func isBackgroundAudioConfigured() -> Bool {
    let modes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String]
    return modes?.contains(audioBackgroundMode) ?? false
}

public class ExpoPrecisionMetronomeModule: Module {
    private var engine: MetronomeEngine?

    public func definition() -> ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onStop")

        OnCreate {
            let eng = MetronomeEngine()
            eng.beatHandler = { [weak self] beat, timestamp, accent in
                self?.sendEvent("onBeat", ["beat": beat, "timestamp": timestamp, "accent": accent])
            }
            eng.stopHandler = { [weak self] reason in
                self?.sendEvent("onStop", ["reason": reason])
            }
            self.engine = eng
        }

        OnDestroy {
            self.engine?.stop(reason: nil)
            self.engine = nil
        }

        AsyncFunction("start") { (bpm: Double, options: StartOptions?) in
            guard bpm >= bpmMin, bpm <= bpmMax else {
                let msg = "BPM must be between \(Int(bpmMin)) and \(Int(bpmMax)), got \(bpm)"
                throw NSError(domain: "ExpoPrecisionMetronome", code: 1, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            if options?.background != nil, !isBackgroundAudioConfigured() {
                throw BackgroundNotConfiguredException()
            }
            try self.engine?.start(bpm: bpm, mixWithOthers: options?.mixWithOthers ?? false)
        }

        AsyncFunction("stop") {
            self.engine?.stop(reason: "explicit")
        }

        AsyncFunction("setBpm") { (bpm: Double) in
            guard bpm >= bpmMin, bpm <= bpmMax else {
                let msg = "BPM must be between \(Int(bpmMin)) and \(Int(bpmMax)), got \(bpm)"
                throw NSError(domain: "ExpoPrecisionMetronome", code: 1, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            self.engine?.setBpm(bpm: bpm)
        }

        AsyncFunction("setSound") { (preset: SoundPreset) in
            self.engine?.setSound(preset: preset)
        }

        AsyncFunction("setPattern") { (pattern: [BeatAccent]) in
            self.engine?.setPattern(pattern)
        }
    }
}

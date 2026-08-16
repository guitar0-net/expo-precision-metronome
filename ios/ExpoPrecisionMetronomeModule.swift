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
            + "Add the config plugin to your app config — "
            + "[\"expo-precision-metronome\", { \"backgroundAudio\": true }] — then run "
            + "`npx expo prebuild`. Projects that never prebuild must add it to "
            + "Info.plist by hand."
    }
}

/// Reading the merged Info.plist is the only reliable way to tell whether the
/// config plugin was applied — the entitlement lives in the app bundle, not here.
private func isBackgroundAudioConfigured() -> Bool {
    let modes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String]
    return modes?.contains(audioBackgroundMode) ?? false
}

/// Both entry points that take a tempo reject the same range with the same message,
/// so neither can drift from the bounds JS validates against.
private func assertBpm(_ bpm: Double) throws {
    guard bpm >= bpmMin, bpm <= bpmMax else {
        let msg = "BPM must be between \(Int(bpmMin)) and \(Int(bpmMax)), got \(bpm)"
        throw NSError(
            domain: "ExpoPrecisionMetronome",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: msg]
        )
    }
}

public class ExpoPrecisionMetronomeModule: Module {
    private var engine: MetronomeEngine?

    public func definition() -> ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onPlaybackChange")

        OnCreate {
            let eng = MetronomeEngine()
            eng.beatHandler = { [weak self] beat, timestamp, accent in
                self?.sendEvent("onBeat", ["beat": beat, "timestamp": timestamp, "accent": accent])
            }
            eng.playbackHandler = { [weak self] state, reason in
                self?.sendEvent("onPlaybackChange", ["state": state.rawValue, "reason": reason])
            }
            self.engine = eng
        }

        OnDestroy {
            self.engine?.stop(reason: nil)
            self.engine = nil
        }

        AsyncFunction("start") { (bpm: Double, options: StartOptions?) in
            try assertBpm(bpm)
            if options?.background != nil, !isBackgroundAudioConfigured() {
                throw BackgroundNotConfiguredException()
            }
            try self.engine?.start(bpm: bpm, mixWithOthers: options?.mixWithOthers ?? false)
        }

        AsyncFunction("stop") {
            self.engine?.stop(reason: "explicit")
        }

        // Idempotent, and deliberately not a toggle: with commands able to originate
        // outside JS, a lost race has to stay harmless rather than invert into the
        // opposite action. A call that changes nothing emits nothing.
        AsyncFunction("pause") {
            self.engine?.pause(reason: "explicit")
        }

        AsyncFunction("resume") {
            self.engine?.resume(reason: "explicit")
        }

        /// `notificationVisible` is always false: iOS has no metronome notification,
        /// see the MediaSession rejection in the pause/resume design notes.
        AsyncFunction("getState") { () -> [String: Any] in
            [
                "state": (self.engine?.playbackState ?? .stopped).rawValue,
                "bpm": self.engine?.bpm ?? 0,
                "notificationVisible": false
            ]
        }

        /// There is no metronome notification on iOS — see the MediaSession rejection in
        /// the pause/resume design notes — so there is nothing to ask for. Both resolve
        /// as granted rather than throwing, to spare every caller a platform branch
        /// around a call that has nothing to do on this platform.
        ///
        /// `canAskAgain` is true for the same reason it is true for a granted Android
        /// permission: nothing here is a denial the caller has to route around.
        AsyncFunction("requestNotificationPermission") { () -> Bool in
            true
        }

        AsyncFunction("getNotificationPermission") { () -> [String: Any] in
            ["status": "granted", "canAskAgain": true]
        }

        AsyncFunction("setBpm") { (bpm: Double) in
            try assertBpm(bpm)
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

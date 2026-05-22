import ExpoModulesCore

private let BPM_MIN: Double = 20
private let BPM_MAX: Double = 300

public class ExpoPrecisionMetronomeModule: Module {
    private var engine: MetronomeEngine?

    public func definition() -> ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onStop")

        OnCreate {
            let eng = MetronomeEngine()
            eng.beatHandler = { [weak self] beat, timestamp in
                self?.sendEvent("onBeat", ["beat": beat, "timestamp": timestamp])
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

        AsyncFunction("start") { (bpm: Double) in
            guard bpm >= BPM_MIN, bpm <= BPM_MAX else {
                throw NSError(
                    domain: "ExpoPrecisionMetronome",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "BPM must be between \(Int(BPM_MIN)) and \(Int(BPM_MAX)), got \(bpm)"]
                )
            }
            try self.engine?.start(bpm: bpm)
        }

        AsyncFunction("stop") {
            self.engine?.stop(reason: "explicit")
        }

        AsyncFunction("setBpm") { (bpm: Double) in
            guard bpm >= BPM_MIN, bpm <= BPM_MAX else {
                throw NSError(
                    domain: "ExpoPrecisionMetronome",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "BPM must be between \(Int(BPM_MIN)) and \(Int(BPM_MAX)), got \(bpm)"]
                )
            }
            self.engine?.setBpm(bpm: bpm)
        }
    }
}

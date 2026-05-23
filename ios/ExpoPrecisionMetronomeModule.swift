import ExpoModulesCore

private let bpmMin: Double = 20
private let bpmMax: Double = 300

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
            guard bpm >= bpmMin, bpm <= bpmMax else {
                let msg = "BPM must be between \(Int(bpmMin)) and \(Int(bpmMax)), got \(bpm)"
                throw NSError(domain: "ExpoPrecisionMetronome", code: 1, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            try self.engine?.start(bpm: bpm)
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
    }
}

import AVFoundation

final class MetronomeEngine {
    // All three handlers are dispatched on the main thread.
    var beatHandler: ((_ beat: Int, _ timestamp: Double, _ accent: String) -> Void)?
    var stopHandler: ((_ reason: String) -> Void)?

    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private let scheduler = BeatScheduler()

    // Accessed exclusively from the audio render thread after start().
    private var sampleRate: Double = 44_100
    private var currentSample: Int64 = 0
    private var clickPhase: Int = -1
    private var clickDurationSamples: Int = 0
    private var clickPreset: SoundPreset = .click
    // Accent params captured at click onset; used for multi-buffer continuation.
    private var clickAccent: ClickSynthesizer.AccentParams = .normal

    // Written from the JS thread, read from the audio render thread.
    // On ARM64 (all iOS devices), an aligned 8-byte load/store is a single LDR/STR —
    // hardware-atomic, no torn reads possible. No synchronisation primitive needed.
    private var currentBPM: Double = 120
    private var currentPresetIndex: Int = 0
    // Pattern packed as: bits 32-36 = (length-1), bits 0-31 = 16×2-bit accent codes.
    // 0b00=strong, 0b01=normal, 0b10=muted. Default: ['strong','normal','normal','normal'].
    private var currentPatternEncoded: Int = MetronomeEngine.defaultPatternEncoded

    private static let defaultPatternEncoded: Int = (3 << 32) | 0x54

    var isRunning: Bool { engine?.isRunning == true }

    // MARK: - Public API

    func start(bpm: Double) throws {
        currentBPM = bpm

        if engine?.isRunning == true {
            return
        }

        try launchEngine()
    }

    func setBpm(bpm: Double) {
        currentBPM = bpm
    }

    func setSound(preset: SoundPreset) {
        currentPresetIndex = SoundPreset.allCases.firstIndex(of: preset) ?? 0
    }

    func setPattern(_ pattern: [BeatAccent]) {
        currentPatternEncoded = MetronomeEngine.encode(pattern)
    }

    /// `reason: nil` suppresses the onStop event — used by OnDestroy where JS is gone.
    func stop(reason: String?) {
        guard let eng = engine else { return }
        removeInterruptionObserver()
        eng.stop()
        sourceNode = nil
        engine = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if let reason {
            stopHandler?(reason)
        }
    }

    // MARK: - Private

    private static func encode(_ pattern: [BeatAccent]) -> Int {
        let len = (pattern.count - 1) << 32
        var bits = 0
        for (beatIdx, accent) in pattern.enumerated() {
            let code: Int
            switch accent {
            case .strong: code = 0
            case .normal: code = 1
            case .muted:  code = 2
            }
            bits |= (code << (beatIdx * 2))
        }
        return len | bits
    }

    private static func decodeAccent(encoded: Int, beatNumber: Int) -> BeatAccent {
        let length = ((encoded >> 32) & 0x1F) + 1
        let beatIndex = beatNumber % length
        let code = (encoded >> (beatIndex * 2)) & 0x3
        switch code {
        case 0:  return .strong
        case 2:  return .muted
        default: return .normal
        }
    }

    private func launchEngine() throws {
        let newEngine = AVAudioEngine()

        let hwRate = newEngine.outputNode.outputFormat(forBus: 0).sampleRate
        let sr = hwRate > 0 ? hwRate : 44_100

        sampleRate = sr
        currentSample = 0
        clickPhase = -1
        scheduler.reset()

        guard let format = AVAudioFormat(standardFormatWithSampleRate: sr, channels: 1) else {
            throw NSError(
                domain: "ExpoPrecisionMetronome",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Failed to create audio format for sample rate \(sr)"]
            )
        }
        let node = AVAudioSourceNode(format: format) { [weak self] _, _, frameCount, audioBufferList in
            self?.renderCallback(frameCount: Int(frameCount), audioBufferList: audioBufferList) ?? noErr
        }

        newEngine.attach(node)
        newEngine.connect(node, to: newEngine.mainMixerNode, format: format)

        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try AVAudioSession.sharedInstance().setActive(true)
        try newEngine.start()

        sourceNode = node
        engine = newEngine

        addInterruptionObserver()
    }

    private func renderOngoingClick(into buffer: UnsafeMutablePointer<Float>, frameCount: Int) {
        guard clickPhase >= 0 else { return }
        let remaining = clickDurationSamples - clickPhase
        let toWrite = min(remaining, frameCount)
        ClickSynthesizer.render(
            into: buffer,
            startFrame: 0,
            clickPhase: clickPhase,
            count: toWrite,
            sampleRate: sampleRate,
            preset: clickPreset,
            accent: clickAccent
        )
        clickPhase += toWrite
        if clickPhase >= clickDurationSamples { clickPhase = -1 }
    }

    private func renderCallback(
        frameCount: Int,
        audioBufferList: UnsafeMutablePointer<AudioBufferList>
    ) -> OSStatus {
        let ablPointer = UnsafeMutableAudioBufferListPointer(audioBufferList)
        guard let rawBuffer = ablPointer[0].mData else { return noErr }
        let buffer = rawBuffer.bindMemory(to: Float.self, capacity: frameCount)

        memset(buffer, 0, frameCount * MemoryLayout<Float>.stride)

        let bpm = currentBPM
        let bufferStart = currentSample

        renderOngoingClick(into: buffer, frameCount: frameCount)

        if let (offset, beatNumber) = scheduler.nextBeat(
            frameCount: frameCount,
            currentSample: bufferStart,
            bpm: bpm,
            sampleRate: sampleRate
        ) {
            let preset = SoundPreset.allCases[currentPresetIndex]
            let accent = MetronomeEngine.decodeAccent(encoded: currentPatternEncoded, beatNumber: beatNumber)
            let ap = ClickSynthesizer.accentParams(for: accent, preset: preset)
            let dur = ClickSynthesizer.clickDuration(sampleRate: sampleRate, preset: preset, accent: accent)
            let toWrite = min(dur, frameCount - offset)
            ClickSynthesizer.render(
                into: buffer,
                startFrame: offset,
                clickPhase: 0,
                count: toWrite,
                sampleRate: sampleRate,
                preset: preset,
                accent: ap
            )
            clickDurationSamples = dur
            clickPreset = preset
            clickAccent = ap
            clickPhase = toWrite < dur ? toWrite : -1

            let beatTimestamp = Double(bufferStart + Int64(offset)) / sampleRate
            let accentName = accent.rawValue
            DispatchQueue.main.async { [weak self] in
                self?.beatHandler?(beatNumber, beatTimestamp, accentName)
            }
        }

        currentSample += Int64(frameCount)
        return noErr
    }

    // MARK: - AVAudioSession interruption

    private func addInterruptionObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )
    }

    private func removeInterruptionObserver() {
        NotificationCenter.default.removeObserver(
            self,
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
    }

    @objc private func handleInterruption(_ notification: Notification) {
        guard
            let info = notification.userInfo,
            let typeValue = info[AVAudioSessionInterruptionTypeKey] as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: typeValue),
            type == .began
        else { return }

        stop(reason: "interruption")
    }

    deinit {
        removeInterruptionObserver()
    }
}

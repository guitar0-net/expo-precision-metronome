import AVFoundation

final class MetronomeEngine {
    // Both handlers are dispatched on the main thread.
    var beatHandler: ((_ beat: Int, _ timestamp: Double) -> Void)?
    var stopHandler: ((_ reason: String) -> Void)?

    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private let scheduler = BeatScheduler()

    // Accessed exclusively from the audio render thread after start().
    private var sampleRate: Double = 44_100
    private var currentSample: Int64 = 0  // relative to engine start
    private var clickPhase: Int = -1      // -1 = no active click, >=0 = samples written so far
    // Duration and preset of the currently-playing click; captured at click onset.
    private var clickDurationSamples: Int = 0
    private var clickPreset: SoundPreset = .click

    // Written from the JS thread, read from the audio render thread.
    // On ARM64 (all iOS devices), an aligned 8-byte load/store is a single LDR/STR —
    // hardware-atomic, no torn reads possible. No synchronisation primitive needed.
    private var currentBPM: Double = 120
    private var currentPresetIndex: Int = 0  // index into SoundPreset.allCases

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

    private func launchEngine() throws {
        let newEngine = AVAudioEngine()

        // outputFormat.sampleRate may be 0 before the engine is started on some devices.
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

        if clickPhase >= 0 {
            let remaining = clickDurationSamples - clickPhase
            let toWrite = min(remaining, frameCount)
            ClickSynthesizer.render(
                into: buffer,
                startFrame: 0,
                clickPhase: clickPhase,
                count: toWrite,
                sampleRate: sampleRate,
                preset: clickPreset
            )
            clickPhase += toWrite
            if clickPhase >= clickDurationSamples { clickPhase = -1 }
        }

        if let (offset, beatNumber) = scheduler.nextBeat(
            frameCount: frameCount,
            currentSample: bufferStart,
            bpm: bpm,
            sampleRate: sampleRate
        ) {
            let preset = SoundPreset.allCases[currentPresetIndex]
            let dur = ClickSynthesizer.clickDuration(sampleRate: sampleRate, preset: preset)
            let toWrite = min(dur, frameCount - offset)
            ClickSynthesizer.render(
                into: buffer,
                startFrame: offset,
                clickPhase: 0,
                count: toWrite,
                sampleRate: sampleRate,
                preset: preset
            )
            clickDurationSamples = dur
            clickPreset = preset
            clickPhase = toWrite < dur ? toWrite : -1

            let beatTimestamp = Double(bufferStart + Int64(offset)) / sampleRate
            let bn = beatNumber
            let ts = beatTimestamp
            DispatchQueue.main.async { [weak self] in
                self?.beatHandler?(bn, ts)
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

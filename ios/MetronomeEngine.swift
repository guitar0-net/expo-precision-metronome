import AVFoundation

final class MetronomeEngine {
    // Both handlers are dispatched on the main thread.
    var beatHandler: ((_ beat: Int, _ timestamp: Double, _ accent: String) -> Void)?
    var playbackHandler: ((_ state: PlaybackState, _ reason: String) -> Void)?

    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private let scheduler = BeatScheduler()

    // Accessed exclusively from the audio render thread after start().
    private var sampleRate: Double = 44_100
    private var currentSample: Int64 = 0
    private var clickPhase: Int = -1
    /// Audio-thread-only mirror of `isPaused`, so the callback can spot the moment it
    /// resumes and restart the bar there — the scheduler must not be touched from the
    /// JS thread.
    private var wasPaused = false
    private var clickDurationSamples: Int = 0
    private var clickPreset: SoundPreset = .click
    // Accent params captured at click onset; used for multi-buffer continuation.
    private var clickAccent: ClickSynthesizer.AccentParams = .normal

    // Written from the JS thread, read from the audio render thread.
    // On ARM64 (all iOS devices), an aligned 8-byte load/store is a single LDR/STR —
    // hardware-atomic, no torn reads possible. No synchronisation primitive needed.
    private var currentBPM: Double = 120
    private var isPaused: Bool = false
    /// Whether the current pause came from an `AVAudioSession` interruption, and is
    /// therefore the only one `.ended` may undo.
    private var pausedByInterruption = false
    private var currentPresetIndex: Int = 0
    // Pattern packed as: bits 32-36 = (length-1), bits 0-31 = 16×2-bit accent codes.
    // 0b00=strong, 0b01=normal, 0b10=muted. Default: ['strong','normal','normal','normal'].
    private var currentPatternEncoded: Int = MetronomeEngine.defaultPatternEncoded

    private static let defaultPatternEncoded: Int = (3 << 32) | 0x54

    var isRunning: Bool { engine?.isRunning == true }

    /// The state JS is told about, and the one `getState()` reports.
    private(set) var playbackState: PlaybackState = .stopped

    var bpm: Double { currentBPM }

    // MARK: - Public API

    /// `mixWithOthers: true` lets backing tracks from other apps keep playing —
    /// the common case for a metronome.
    ///
    /// Calling this on a live engine restarts it. The session category is only
    /// applied when the session is activated, so without the restart a second
    /// `start()` would silently keep the previous call's `mixWithOthers`.
    ///
    /// The test is `playbackState`, not `isRunning`: an interruption stops the
    /// `AVAudioEngine` under us while the session stays paused, and tearing the old
    /// one down is what keeps `launchEngine` from stacking a second interruption
    /// observer on top of the first.
    func start(bpm: Double, mixWithOthers: Bool = false) throws {
        if playbackState != .stopped {
            // Silent: JS asked for a restart, not for a stop.
            stop(reason: nil)
        }

        currentBPM = bpm
        isPaused = false
        pausedByInterruption = false
        try launchEngine(mixWithOthers: mixWithOthers)
        playbackState = .running
    }

    /// Silences the render callback and leaves everything else standing — the engine,
    /// the source node and, deliberately, the audio session.
    ///
    /// Deactivating the session on pause would stop background audio output, so iOS
    /// would suspend the app: `resume()` from JS becomes unreachable (JS is not
    /// running, and iOS has no metronome notification), and the `.ended` interruption
    /// notification never arrives, so auto-resume after a call would only work in the
    /// foreground — the one case where it is least needed. The cost is that under
    /// `mixWithOthers: false` the `.playback` category keeps silencing other audio
    /// while paused, which is not the primary metronome scenario.
    ///
    /// Android is the mirror image and releases audio focus on pause. There is no
    /// symmetry to find: the platforms bind "audible" and "alive" differently.
    ///
    /// Returns whether this call was the one that paused. `reason: nil` is silent.
    @discardableResult
    func pause(reason: String?) -> Bool {
        guard playbackState == .running else { return false }
        isPaused = true
        playbackState = .paused
        if let reason {
            playbackHandler?(.paused, reason)
        }
        return true
    }

    /// Restarts the bar — the scheduler resets on the audio thread, so playback always
    /// re-enters on the downbeat.
    @discardableResult
    func resume(reason: String?) -> Bool {
        guard playbackState == .paused else { return false }

        // A system interruption deactivates the session and stops the engine under us,
        // so both have to be standing again before the callback is unmuted. Both are
        // no-ops when the pause was the user's own.
        try? AVAudioSession.sharedInstance().setActive(true)
        if let eng = engine, !eng.isRunning {
            do {
                try eng.start()
            } catch {
                // Report the truth rather than claim playback that produces nothing.
                stop(reason: reason)
                return false
            }
        }

        isPaused = false
        pausedByInterruption = false
        playbackState = .running
        if let reason {
            playbackHandler?(.running, reason)
        }
        return true
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

    /// `reason: nil` transitions silently — used by a restart, and by OnDestroy where
    /// JS is gone.
    func stop(reason: String?) {
        guard let eng = engine else { return }
        removeInterruptionObserver()
        eng.stop()
        sourceNode = nil
        engine = nil
        playbackState = .stopped
        // Or the next start() would inherit a pause nobody asked for.
        isPaused = false
        pausedByInterruption = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if let reason {
            playbackHandler?(.stopped, reason)
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

    private func launchEngine(mixWithOthers: Bool) throws {
        let newEngine = AVAudioEngine()

        let hwRate = newEngine.outputNode.outputFormat(forBus: 0).sampleRate
        let sr = hwRate > 0 ? hwRate : 44_100

        sampleRate = sr
        currentSample = 0
        clickPhase = -1
        wasPaused = false
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

        try AVAudioSession.sharedInstance().setCategory(
            .playback,
            mode: .default,
            options: mixWithOthers ? [.mixWithOthers] : []
        )
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

        // The in-flight click is allowed to finish into a pause: cutting it mid-way is
        // a discontinuity the user hears as a pop, and no click outlives 250 ms.
        renderOngoingClick(into: buffer, frameCount: frameCount)

        // Paused writes silence rather than tearing the engine down: rebuilding costs
        // the sample accuracy this package exists for, and metronome pauses are short.
        // The sample clock runs on, so beat timestamps stay on one timeline.
        if isPaused {
            wasPaused = true
            currentSample += Int64(frameCount)
            return noErr
        }

        // Resuming restarts the bar: pause is pressed by ear at an arbitrary moment, so
        // a musician re-enters on "one".
        if wasPaused {
            wasPaused = false
            scheduler.reset()
        }

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
            let type = AVAudioSession.InterruptionType(rawValue: typeValue)
        else { return }

        let phase: InterruptionPhase
        switch type {
        case .began: phase = .began
        case .ended: phase = .ended
        @unknown default: return
        }

        let rawOptions = info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
        let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions)

        switch InterruptionPolicy.outcome(
            phase: phase,
            shouldResume: options.contains(.shouldResume),
            pausedByInterruption: pausedByInterruption
        ) {
        case .pause:
            // Recorded only when this call was the one that paused, so an interruption
            // arriving during a pause the user asked for cannot license an auto-resume.
            if pause(reason: "interruption") {
                pausedByInterruption = true
            }

        case .resume:
            resume(reason: "interruption")

        case .ignore:
            // Staying paused is a real outcome, not a dropped event: resuming is still
            // one tap away in the app.
            if phase == .ended {
                pausedByInterruption = false
            }
        }
    }

    deinit {
        removeInterruptionObserver()
    }
}

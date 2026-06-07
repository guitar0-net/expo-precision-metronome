import Foundation
import Testing
@testable import MetronomeCore

@Suite struct ClickSynthesizerTests {

    @Test func samplesBeforeOffsetRemainZero() {
        let bufferSize = 100
        let startFrame = 10
        var buffer = [Float](repeating: 0, count: bufferSize)

        buffer.withUnsafeMutableBufferPointer { ptr in
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: startFrame, clickPhase: 0, count: 20, sampleRate: 44_100, preset: .click)
        }

        for i in 0..<startFrame {
            #expect(buffer[i] == 0, "sample \(i) before startFrame should be zero")
        }
    }

    @Test func samplesAtAndAfterOffsetAreNonZero() {
        let bufferSize = 100
        let startFrame = 10
        var buffer = [Float](repeating: 0, count: bufferSize)

        buffer.withUnsafeMutableBufferPointer { ptr in
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: startFrame, clickPhase: 0, count: 20, sampleRate: 44_100, preset: .click)
        }

        // clickPhase 0 → sin(0) = 0, so startFrame itself is zero; every subsequent
        // sample in the rendered range must be non-zero (sin(2π·1000·k/sr) ≠ 0 for k ≥ 1).
        for i in (startFrame + 1)..<(startFrame + 20) {
            #expect(buffer[i] != 0, "sample \(i) in rendered range should be non-zero")
        }
        for i in (startFrame + 20)..<bufferSize {
            #expect(buffer[i] == 0, "sample \(i) beyond rendered range should remain zero")
        }
    }

    @Test(arguments: [44_100.0, 48_000.0])
    func clickDurationIsPositiveForAllPresets(sampleRate: Double) {
        for preset in SoundPreset.allCases {
            let dur = ClickSynthesizer.clickDuration(sampleRate: sampleRate, preset: preset)
            #expect(dur > 0, "\(preset) clickDuration should be positive at \(sampleRate) Hz")
        }
    }

    @Test func cowbellIsLongerThanClick() {
        let sr = 44_100.0
        #expect(
            ClickSynthesizer.clickDuration(sampleRate: sr, preset: .cowbell) >
            ClickSynthesizer.clickDuration(sampleRate: sr, preset: .click),
            "cowbell (250 ms) should be longer than click (10 ms)"
        )
    }

    @Test(arguments: SoundPreset.allCases)
    func eachPresetRendersNonZeroSamples(preset: SoundPreset) {
        let sr = 44_100.0
        let count = ClickSynthesizer.clickDuration(sampleRate: sr, preset: preset)
        var buffer = [Float](repeating: 0, count: count)
        buffer.withUnsafeMutableBufferPointer { ptr in
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: 0, clickPhase: 1, count: count, sampleRate: sr, preset: preset)
        }
        let anyNonZero = buffer.contains { $0 != 0 }
        #expect(anyNonZero, "\(preset) render produced all-zero output")
    }

    @Test func renderAccumulatesIntoBuffer() {
        let count = 20
        var singlePass = [Float](repeating: 0, count: count)
        var twoPass    = [Float](repeating: 0, count: count)

        singlePass.withUnsafeMutableBufferPointer { ptr in
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: 0, clickPhase: 0, count: count, sampleRate: 44_100, preset: .click)
        }
        twoPass.withUnsafeMutableBufferPointer { ptr in
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: 0, clickPhase: 0, count: count, sampleRate: 44_100, preset: .click)
            ClickSynthesizer.render(into: ptr.baseAddress!, startFrame: 0, clickPhase: 0, count: count, sampleRate: 44_100, preset: .click)
        }

        for i in 0..<count {
            #expect(abs(twoPass[i] - singlePass[i] * 2) <= 1e-6, "sample \(i) should be doubled after two render passes")
        }
    }
}

@Suite struct BeatSchedulerTests {

    // MARK: - Interval accuracy

    @Test func beatIntervalMatchesFormula() throws {
        let sr = 44_100.0
        let bpm = 120.0
        let expectedInterval = Int64(sr * 60.0 / bpm)  // 22050
        let scheduler = BeatScheduler()

        let beat0 = try #require(scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: bpm, sampleRate: sr))
        #expect(beat0.offset == 0)
        #expect(beat0.beatNumber == 0)

        let beat1 = try #require(
            scheduler.nextBeat(frameCount: 1, currentSample: expectedInterval, bpm: bpm, sampleRate: sr),
            "second beat should fire at sample \(expectedInterval)"
        )
        #expect(beat1.offset == 0)
        #expect(beat1.beatNumber == 1)
    }

    @Test func beatIntervalOneFrameEarlierDoesNotFire() {
        let sr = 44_100.0
        let bpm = 120.0
        let expectedInterval = Int64(sr * 60.0 / bpm)
        let scheduler = BeatScheduler()
        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: bpm, sampleRate: sr)

        let noBeat = scheduler.nextBeat(frameCount: 1, currentSample: expectedInterval - 1, bpm: bpm, sampleRate: sr)
        #expect(noBeat == nil, "beat should not fire one frame before the scheduled interval")
    }

    @Test func firstBeatFiresImmediatelyAtNonZeroStart() throws {
        let scheduler = BeatScheduler()
        let beat = try #require(
            scheduler.nextBeat(frameCount: 1, currentSample: 99_999, bpm: 120, sampleRate: 44_100),
            "first beat should fire immediately regardless of starting sample"
        )
        #expect(beat.offset == 0)
        #expect(beat.beatNumber == 0)
    }

    // MARK: - BPM change mid-stream

    @Test func bpmChangeDoesNotSkipScheduledBeat() throws {
        let sr = 44_100.0
        let scheduler = BeatScheduler()
        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: 120, sampleRate: sr)

        let beat1 = try #require(
            scheduler.nextBeat(frameCount: 1, currentSample: 22050, bpm: 60, sampleRate: sr),
            "scheduled beat must not be skipped after a BPM change"
        )
        #expect(beat1.beatNumber == 1)
        #expect(beat1.offset == 0)
    }

    @Test func bpmChangeDoesNotProduceDuplicateBeat() throws {
        let sr = 44_100.0
        let scheduler = BeatScheduler()
        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: 60, sampleRate: sr)

        let beat1 = try #require(scheduler.nextBeat(frameCount: 50_000, currentSample: 1, bpm: 300, sampleRate: sr))
        #expect(beat1.offset == 44099)
        #expect(beat1.beatNumber == 1)
    }

    @Test func noBeatBetweenConsecutiveBeatPositions() {
        let sr = 44_100.0
        let scheduler = BeatScheduler()
        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: 120, sampleRate: sr)

        let noBeat = scheduler.nextBeat(frameCount: 22049, currentSample: 1, bpm: 120, sampleRate: sr)
        #expect(noBeat == nil, "no beat should fire between two consecutive beat positions")
    }

    // MARK: - High BPM boundary

    @Test func highBpmBoundaryAt300() throws {
        let sr = 44_100.0
        let bpm = 300.0
        let expectedInterval = Int64(sr * 60.0 / bpm)  // 8820
        let scheduler = BeatScheduler()
        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: bpm, sampleRate: sr)

        let beat1 = try #require(
            scheduler.nextBeat(frameCount: 1, currentSample: expectedInterval, bpm: bpm, sampleRate: sr),
            "beat should fire at expected interval for 300 BPM"
        )
        #expect(beat1.offset == 0)
        #expect(beat1.beatNumber == 1)
    }

    // MARK: - Beat counter

    @Test func beatCounterIsMonotonicallyIncreasing() throws {
        let sr = 44_100.0
        let scheduler = BeatScheduler()
        let interval = Int64(sr * 60.0 / 120.0)

        var prev = -1
        for i in 0..<5 {
            let beat = try #require(
                scheduler.nextBeat(frameCount: 1, currentSample: Int64(i) * interval, bpm: 120, sampleRate: sr),
                "beat \(i) should fire at sample \(Int64(i) * interval)"
            )
            #expect(beat.beatNumber > prev, "beat counter must increase monotonically")
            prev = beat.beatNumber
        }
    }

    @Test func resetClearsBeatCounter() throws {
        let sr = 44_100.0
        let scheduler = BeatScheduler()
        let interval = Int64(sr * 60.0 / 120.0)

        _ = scheduler.nextBeat(frameCount: 1, currentSample: 0, bpm: 120, sampleRate: sr)
        _ = scheduler.nextBeat(frameCount: 1, currentSample: interval, bpm: 120, sampleRate: sr)
        scheduler.reset()

        let beat = try #require(scheduler.nextBeat(frameCount: 1, currentSample: 99_999, bpm: 120, sampleRate: sr))
        #expect(beat.beatNumber == 0, "beat counter should reset to 0 after reset()")
        #expect(beat.offset == 0, "after reset, first beat should fire immediately at any currentSample")
    }
}

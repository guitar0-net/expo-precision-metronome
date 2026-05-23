import Foundation

// All methods are called exclusively from the audio render thread.
final class BeatScheduler {
    private(set) var beatCounter: Int = 0
    private var nextBeatSample: Int64 = 0

    func reset() {
        beatCounter = 0
        nextBeatSample = 0
    }

    // Returns the first beat in this buffer, or nil if none.
    // Assumes at most one beat per buffer, which holds for 20–300 BPM on typical
    // AVAudioEngine buffer sizes (≤ 50 ms). Extend to a loop if BPM limits grow beyond this.
    func nextBeat(
        frameCount: Int,
        currentSample: Int64,
        bpm: Double,
        sampleRate: Double
    ) -> (offset: Int, beatNumber: Int)? {
        let interval = Int64(sampleRate * 60.0 / bpm)
        guard interval > 0 else { return nil }

        // Clamp to currentSample so the very first beat fires at offset 0
        // and not in a "past" position, giving an immediate click on start().
        if nextBeatSample < currentSample {
            nextBeatSample = currentSample
        }

        guard nextBeatSample < currentSample + Int64(frameCount) else { return nil }

        let offset = Int(nextBeatSample - currentSample)
        let beat = beatCounter
        beatCounter += 1
        nextBeatSample += interval
        assert(nextBeatSample >= currentSample + Int64(frameCount),
               "Two beats fall within one buffer — extend nextBeat to a loop")
        return (offset, beat)
    }
}

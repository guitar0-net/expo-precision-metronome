#pragma once
#include <cassert>
#include <cstdint>

class BeatScheduler {
public:
    int beatCounter = 0;

    void reset() {
        beatCounter = 0;
        nextBeatSample_ = 0;
    }

    struct BeatResult {
        int offset;
        int beatNumber;
    };

    // Returns the first beat in this buffer, or {-1, -1} if none.
    // Assumes at most one beat per buffer, which holds for 20–300 BPM on typical
    // Oboe buffer sizes (≤ 50 ms). Extend to a loop if BPM limits grow beyond this.
    BeatResult nextBeat(int frameCount, int64_t currentSample, double bpm, double sampleRate) {
        if (bpm <= 0 || sampleRate <= 0) return {-1, -1};
        int64_t interval = static_cast<int64_t>(sampleRate * 60.0 / bpm);
        if (interval <= 0) return {-1, -1};

        // Clamp so the very first beat fires immediately at offset 0.
        if (nextBeatSample_ < currentSample) {
            nextBeatSample_ = currentSample;
        }

        if (nextBeatSample_ >= currentSample + frameCount) return {-1, -1};

        int offset = static_cast<int>(nextBeatSample_ - currentSample);
        int beat = beatCounter++;
        nextBeatSample_ += interval;
        assert(nextBeatSample_ >= currentSample + frameCount &&
               "Two beats fall within one buffer — extend nextBeat to a loop");
        return {offset, beat};
    }

private:
    int64_t nextBeatSample_ = 0;
};

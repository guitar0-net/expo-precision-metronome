#pragma once
#include <cmath>

namespace ClickSynthesizer {

static constexpr double kFrequency      = 1000.0;
static constexpr double kClickSeconds   = 0.010;
// Envelope spans 5 time-constants; exp(-5) ≈ 0.007 residual amplitude at end.
static constexpr double kDecayTau       = kClickSeconds / 5.0;

inline int clickDuration(double sampleRate) {
    return static_cast<int>(kClickSeconds * sampleRate);
}

// Writes `count` samples starting at `buffer[startFrame]`.
// `clickPhase` is the sample index within the click (0 = click onset).
inline void render(float* buffer, int startFrame, int clickPhase, int count, double sampleRate) {
    if (count <= 0) return;
    const double twoPiF = 2.0 * M_PI * kFrequency;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        float sample = static_cast<float>(std::sin(twoPiF * t) * std::exp(-t / kDecayTau));
        buffer[startFrame + i] += sample;
    }
}

} // namespace ClickSynthesizer

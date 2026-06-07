#pragma once
#include <cmath>
#include <cstdint>

enum class SoundPreset : int {
    Click     = 0,
    Beep      = 1,
    Woodblock = 2,
    Rim       = 3,
    Hihat     = 4,
    Cowbell   = 5,
};

namespace ClickSynthesizer {

inline double clickSeconds(SoundPreset preset) {
    switch (preset) {
        case SoundPreset::Click:     return 0.010;
        case SoundPreset::Beep:      return 0.020;
        case SoundPreset::Woodblock: return 0.008;
        case SoundPreset::Rim:       return 0.006;
        case SoundPreset::Hihat:     return 0.008;
        case SoundPreset::Cowbell:   return 0.250;
        default:                     return 0.010;
    }
}

inline int clickDuration(double sampleRate, SoundPreset preset) {
    return static_cast<int>(clickSeconds(preset) * sampleRate);
}

inline void renderSine(float* buffer, int startFrame, int clickPhase, int count,
                       double sampleRate, double freq, double decayTau) {
    const double twoPiF = 2.0 * M_PI * freq;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        buffer[startFrame + i] += static_cast<float>(std::sin(twoPiF * t) * std::exp(-t / decayTau));
    }
}

inline void renderDualSine(float* buffer, int startFrame, int clickPhase, int count,
                           double sampleRate, double freq1, double freq2, double decayTau) {
    const double twoPiF1 = 2.0 * M_PI * freq1;
    const double twoPiF2 = 2.0 * M_PI * freq2;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        float amp = static_cast<float>(std::exp(-t / decayTau));
        buffer[startFrame + i] += static_cast<float>((std::sin(twoPiF1 * t) + std::sin(twoPiF2 * t)) * 0.5) * amp;
    }
}

inline void renderNoise(float* buffer, int startFrame, int clickPhase, int count,
                        double sampleRate, double decayTau) {
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        float amp = static_cast<float>(std::exp(-t / decayTau));
        uint32_t x = static_cast<uint32_t>(clickPhase + i);
        x = x * 1664525u + 1013904223u;
        x = (x ^ (x >> 16)) * 0x45d9f3b7u;
        x ^= (x >> 16);
        float noise = (static_cast<float>(x >> 1) / static_cast<float>(1u << 31)) * 2.0f - 1.0f;
        buffer[startFrame + i] += noise * amp;
    }
}

inline void render(float* buffer, int startFrame, int clickPhase, int count,
                   double sampleRate, SoundPreset preset) {
    if (count <= 0) return;
    switch (preset) {
        case SoundPreset::Click:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 1000.0, 0.002);
            break;
        case SoundPreset::Beep:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 880.0, 0.008);
            break;
        case SoundPreset::Woodblock:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 400.0, 0.001);
            break;
        case SoundPreset::Rim:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate, 800.0, 1600.0, 0.0012);
            break;
        case SoundPreset::Hihat:
            renderNoise(buffer, startFrame, clickPhase, count, sampleRate, 0.0015);
            break;
        case SoundPreset::Cowbell:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate, 562.0, 845.0, 0.05);
            break;
        default:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 1000.0, 0.002);
            break;
    }
}

} // namespace ClickSynthesizer

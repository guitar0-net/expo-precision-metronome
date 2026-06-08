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
// These values must match Kotlin SoundPreset.ordinal — reordering either enum breaks the JNI bridge.
static_assert(static_cast<int>(SoundPreset::Click)     == 0, "ordinal mismatch: Click");
static_assert(static_cast<int>(SoundPreset::Beep)      == 1, "ordinal mismatch: Beep");
static_assert(static_cast<int>(SoundPreset::Woodblock) == 2, "ordinal mismatch: Woodblock");
static_assert(static_cast<int>(SoundPreset::Rim)       == 3, "ordinal mismatch: Rim");
static_assert(static_cast<int>(SoundPreset::Hihat)     == 4, "ordinal mismatch: Hihat");
static_assert(static_cast<int>(SoundPreset::Cowbell)   == 5, "ordinal mismatch: Cowbell");

// 0=strong, 1=normal, 2=muted — matches Kotlin BeatAccent.ordinal.
enum class BeatAccent : int {
    Strong = 0,
    Normal = 1,
    Muted  = 2,
};

namespace ClickSynthesizer {

struct AccentParams {
    float  volume;    // amplitude multiplier
    double freqMult;  // frequency multiplier (strong has higher pitch)
    double decayMult; // decay-tau multiplier  (strong decays faster → punchier)
};

// strong: louder, higher pitch, punchier — the secret sauce; muted: ghost sound at ~12 % amplitude.
inline AccentParams accentParams(BeatAccent accent) {
    switch (accent) {
        case BeatAccent::Strong: return { 1.3f, 1.4, 0.6 };
        case BeatAccent::Muted:  return { 0.12f, 1.0, 1.0 };
        default:                 return { 1.0f, 1.0, 1.0 };
    }
}

// Hihat strong = open hihat: louder + longer decay. freqMult is unused by renderNoise.
inline AccentParams accentParams(BeatAccent accent, SoundPreset preset) {
    if (preset != SoundPreset::Hihat) return accentParams(accent);
    switch (accent) {
        case BeatAccent::Strong: return { 1.4f, 1.0, 3.5 };
        case BeatAccent::Muted:  return { 0.12f, 1.0, 1.0 };
        default:                 return { 1.0f, 1.0, 1.0 };
    }
}

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

// Hihat strong is longer — like an open hihat ringing out.
inline int clickDuration(double sampleRate, SoundPreset preset, BeatAccent accent) {
    double seconds = (preset == SoundPreset::Hihat && accent == BeatAccent::Strong)
        ? 0.030
        : clickSeconds(preset);
    return static_cast<int>(seconds * sampleRate);
}

inline void renderSine(float* buffer, int startFrame, int clickPhase, int count,
                       double sampleRate, double freq, double decayTau,
                       float volume, double freqMult, double decayMult) {
    const double twoPiF = 2.0 * M_PI * (freq * freqMult);
    const double tau    = decayTau * decayMult;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        buffer[startFrame + i] += static_cast<float>(std::sin(twoPiF * t) * std::exp(-t / tau)) * volume;
    }
}

inline void renderDualSine(float* buffer, int startFrame, int clickPhase, int count,
                           double sampleRate, double freq1, double freq2, double decayTau,
                           float volume, double freqMult, double decayMult) {
    const double twoPiF1 = 2.0 * M_PI * (freq1 * freqMult);
    const double twoPiF2 = 2.0 * M_PI * (freq2 * freqMult);
    const double tau     = decayTau * decayMult;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        float amp = static_cast<float>(std::exp(-t / tau));
        buffer[startFrame + i] += static_cast<float>((std::sin(twoPiF1 * t) + std::sin(twoPiF2 * t)) * 0.5) * amp * volume;
    }
}

inline void renderNoise(float* buffer, int startFrame, int clickPhase, int count,
                        double sampleRate, double decayTau,
                        float volume, double decayMult) {
    const double tau = decayTau * decayMult;
    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(clickPhase + i) / sampleRate;
        float amp = static_cast<float>(std::exp(-t / tau)) * volume;
        uint32_t x = static_cast<uint32_t>(clickPhase + i);
        x = x * 1664525u + 1013904223u;
        x = (x ^ (x >> 16)) * 0x45d9f3b7u;
        x ^= (x >> 16);
        float noise = (static_cast<float>(x >> 1) / static_cast<float>(1u << 31)) * 2.0f - 1.0f;
        buffer[startFrame + i] += noise * amp;
    }
}

inline void render(float* buffer, int startFrame, int clickPhase, int count,
                   double sampleRate, SoundPreset preset, AccentParams accent) {
    if (count <= 0) return;
    const float  v  = accent.volume;
    const double fm = accent.freqMult;
    const double dm = accent.decayMult;
    switch (preset) {
        case SoundPreset::Click:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 1000.0, 0.002, v, fm, dm);
            break;
        case SoundPreset::Beep:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 880.0, 0.008, v, fm, dm);
            break;
        case SoundPreset::Woodblock:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 400.0, 0.001, v, fm, dm);
            break;
        case SoundPreset::Rim:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate, 800.0, 1600.0, 0.0012, v, fm, dm);
            break;
        case SoundPreset::Hihat:
            renderNoise(buffer, startFrame, clickPhase, count, sampleRate, 0.0015, v, dm);
            break;
        case SoundPreset::Cowbell:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate, 562.0, 845.0, 0.05, v, fm, dm);
            break;
        default:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate, 1000.0, 0.002, v, fm, dm);
            break;
    }
}

} // namespace ClickSynthesizer

import Foundation

enum ClickSynthesizer {
    struct AccentParams {
        static let normal = AccentParams(volume: 1.0, freqMult: 1.0, decayMult: 1.0)
        let volume: Float
        let freqMult: Double
        let decayMult: Double
    }

    // strong: louder, higher pitch, punchier decay — the secret sauce.
    // muted:  ghost sound at ~12 % amplitude.
    static func accentParams(for accent: BeatAccent) -> AccentParams {
        switch accent {
        case .strong: return AccentParams(volume: 1.3, freqMult: 1.4, decayMult: 0.6)
        case .muted:  return AccentParams(volume: 0.12, freqMult: 1.0, decayMult: 1.0)
        case .normal: return .normal
        }
    }

    static func clickDuration(sampleRate: Double, preset: SoundPreset) -> Int {
        Int(clickSeconds(preset: preset) * sampleRate)
    }

    // Hihat strong = open hihat: louder + longer decay. freqMult is unused by renderNoise.
    static func accentParams(for accent: BeatAccent, preset: SoundPreset) -> AccentParams {
        guard preset == .hihat else { return accentParams(for: accent) }
        switch accent {
        case .strong: return AccentParams(volume: 1.4, freqMult: 1.0, decayMult: 3.5)
        case .muted:  return AccentParams(volume: 0.12, freqMult: 1.0, decayMult: 1.0)
        case .normal: return .normal
        }
    }

    // Hihat strong is longer — like an open hihat ringing out.
    static func clickDuration(sampleRate: Double, preset: SoundPreset, accent: BeatAccent) -> Int {
        let seconds = (preset == .hihat && accent == .strong) ? 0.030 : clickSeconds(preset: preset)
        return Int(seconds * sampleRate)
    }

    // swiftlint:disable:next function_parameter_count
    static func render(
        into buffer: UnsafeMutablePointer<Float>,
        startFrame: Int,
        clickPhase: Int,
        count: Int,
        sampleRate: Double,
        preset: SoundPreset,
        accent: AccentParams = .normal
    ) {
        guard count > 0 else { return }
        let ctx = RenderContext(
            buffer: buffer,
            startFrame: startFrame,
            clickPhase: clickPhase,
            count: count,
            sampleRate: sampleRate,
            accent: accent
        )
        switch preset {
        case .click:     renderSine(ctx, freq: 1_000, decayTau: 0.002)
        case .beep:      renderSine(ctx, freq: 880, decayTau: 0.008)
        case .woodblock: renderSine(ctx, freq: 400, decayTau: 0.001)
        case .rim:       renderDualSine(ctx, freq1: 800, freq2: 1_600, decayTau: 0.0012)
        case .hihat:     renderNoise(ctx, decayTau: 0.0015)
        case .cowbell:   renderDualSine(ctx, freq1: 562, freq2: 845, decayTau: 0.05)
        }
    }

    // MARK: - Private helpers

    private struct RenderContext {
        let buffer: UnsafeMutablePointer<Float>
        let startFrame: Int
        let clickPhase: Int
        let count: Int
        let sampleRate: Double
        let accent: AccentParams
    }

    private static func clickSeconds(preset: SoundPreset) -> Double {
        switch preset {
        case .click:     return 0.010
        case .beep:      return 0.020
        case .woodblock: return 0.008
        case .rim:       return 0.006
        case .hihat:     return 0.008
        case .cowbell:   return 0.250
        }
    }

    private static func renderSine(_ ctx: RenderContext, freq: Double, decayTau: Double) {
        let twoPiF = 2.0 * Double.pi * (freq * ctx.accent.freqMult)
        let tau = decayTau * ctx.accent.decayMult
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            ctx.buffer[ctx.startFrame + idx] += Float(sin(twoPiF * phase) * exp(-phase / tau)) * ctx.accent.volume
        }
    }

    private static func renderDualSine(
        _ ctx: RenderContext,
        freq1: Double,
        freq2: Double,
        decayTau: Double
    ) {
        let twoPiF1 = 2.0 * Double.pi * (freq1 * ctx.accent.freqMult)
        let twoPiF2 = 2.0 * Double.pi * (freq2 * ctx.accent.freqMult)
        let tau = decayTau * ctx.accent.decayMult
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            let amp = Float(exp(-phase / tau)) * ctx.accent.volume
            ctx.buffer[ctx.startFrame + idx] += Float(sin(twoPiF1 * phase) + sin(twoPiF2 * phase)) * 0.5 * amp
        }
    }

    private static func renderNoise(_ ctx: RenderContext, decayTau: Double) {
        let tau = decayTau * ctx.accent.decayMult
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            let amp = Float(exp(-phase / tau)) * ctx.accent.volume
            var noiseState = UInt32(bitPattern: Int32(truncatingIfNeeded: ctx.clickPhase + idx))
            noiseState = noiseState &* 1_664_525 &+ 1_013_904_223
            noiseState = (noiseState ^ (noiseState >> 16)) &* 0x45d9_f3b7
            noiseState ^= noiseState >> 16
            let noise = (Float(noiseState >> 1) / Float(1 << 31)) * 2.0 - 1.0
            ctx.buffer[ctx.startFrame + idx] += noise * amp
        }
    }
}

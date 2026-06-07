import Foundation

enum ClickSynthesizer {
    static func clickDuration(sampleRate: Double, preset: SoundPreset) -> Int {
        Int(clickSeconds(preset: preset) * sampleRate)
    }

    // swiftlint:disable:next function_parameter_count
    static func render(
        into buffer: UnsafeMutablePointer<Float>,
        startFrame: Int,
        clickPhase: Int,
        count: Int,
        sampleRate: Double,
        preset: SoundPreset
    ) {
        guard count > 0 else { return }
        let ctx = RenderContext(
            buffer: buffer,
            startFrame: startFrame,
            clickPhase: clickPhase,
            count: count,
            sampleRate: sampleRate
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
        let twoPiF = 2.0 * Double.pi * freq
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            ctx.buffer[ctx.startFrame + idx] += Float(sin(twoPiF * phase) * exp(-phase / decayTau))
        }
    }

    private static func renderDualSine(
        _ ctx: RenderContext,
        freq1: Double,
        freq2: Double,
        decayTau: Double
    ) {
        let twoPiF1 = 2.0 * Double.pi * freq1
        let twoPiF2 = 2.0 * Double.pi * freq2
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            let amp = exp(-phase / decayTau)
            ctx.buffer[ctx.startFrame + idx] += Float((sin(twoPiF1 * phase) + sin(twoPiF2 * phase)) * 0.5 * amp)
        }
    }

    private static func renderNoise(_ ctx: RenderContext, decayTau: Double) {
        for idx in 0..<ctx.count {
            let phase = Double(ctx.clickPhase + idx) / ctx.sampleRate
            let amp = Float(exp(-phase / decayTau))
            var noiseState = UInt32(bitPattern: Int32(truncatingIfNeeded: ctx.clickPhase + idx))
            noiseState = noiseState &* 1_664_525 &+ 1_013_904_223
            noiseState = (noiseState ^ (noiseState >> 16)) &* 0x45d9_f3b7
            noiseState ^= noiseState >> 16
            let noise = (Float(noiseState >> 1) / Float(1 << 31)) * 2.0 - 1.0
            ctx.buffer[ctx.startFrame + idx] += noise * amp
        }
    }
}

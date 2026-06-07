import Foundation

enum ClickSynthesizer {
    static func clickDuration(sampleRate: Double, preset: SoundPreset) -> Int {
        Int(clickSeconds(preset: preset) * sampleRate)
    }

    static func render(
        into buffer: UnsafeMutablePointer<Float>,
        startFrame: Int,
        clickPhase: Int,
        count: Int,
        sampleRate: Double,
        preset: SoundPreset
    ) {
        guard count > 0 else { return }
        switch preset {
        case .click:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate,
                       freq: 1_000, decayTau: 0.002)
        case .beep:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate,
                       freq: 880, decayTau: 0.008)
        case .woodblock:
            renderSine(buffer, startFrame, clickPhase, count, sampleRate,
                       freq: 400, decayTau: 0.001)
        case .rim:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate,
                           freq1: 800, freq2: 1_600, decayTau: 0.0012)
        case .hihat:
            renderNoise(buffer, startFrame, clickPhase, count, sampleRate,
                        decayTau: 0.0015)
        case .cowbell:
            renderDualSine(buffer, startFrame, clickPhase, count, sampleRate,
                           freq1: 562, freq2: 845, decayTau: 0.05)
        }
    }

    // MARK: - Private helpers

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

    private static func renderSine(
        _ buffer: UnsafeMutablePointer<Float>,
        _ startFrame: Int, _ clickPhase: Int, _ count: Int, _ sampleRate: Double,
        freq: Double, decayTau: Double
    ) {
        let twoPiF = 2.0 * Double.pi * freq
        for idx in 0..<count {
            let t = Double(clickPhase + idx) / sampleRate
            buffer[startFrame + idx] += Float(sin(twoPiF * t) * exp(-t / decayTau))
        }
    }

    private static func renderDualSine(
        _ buffer: UnsafeMutablePointer<Float>,
        _ startFrame: Int, _ clickPhase: Int, _ count: Int, _ sampleRate: Double,
        freq1: Double, freq2: Double, decayTau: Double
    ) {
        let twoPiF1 = 2.0 * Double.pi * freq1
        let twoPiF2 = 2.0 * Double.pi * freq2
        for idx in 0..<count {
            let t = Double(clickPhase + idx) / sampleRate
            let amp = exp(-t / decayTau)
            buffer[startFrame + idx] += Float((sin(twoPiF1 * t) + sin(twoPiF2 * t)) * 0.5 * amp)
        }
    }

    private static func renderNoise(
        _ buffer: UnsafeMutablePointer<Float>,
        _ startFrame: Int, _ clickPhase: Int, _ count: Int, _ sampleRate: Double,
        decayTau: Double
    ) {
        for idx in 0..<count {
            let t = Double(clickPhase + idx) / sampleRate
            let amp = Float(exp(-t / decayTau))
            var x = UInt32(bitPattern: Int32(truncatingIfNeeded: clickPhase + idx))
            x = x &* 1_664_525 &+ 1_013_904_223
            x = (x ^ (x >> 16)) &* 0x45d9_f3b7
            x ^= x >> 16
            let noise = (Float(x >> 1) / Float(1 << 31)) * 2.0 - 1.0
            buffer[startFrame + idx] += noise * amp
        }
    }
}

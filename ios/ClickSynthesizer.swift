import Foundation

enum ClickSynthesizer {
    private static let frequency: Double = 1_000
    private static let clickSeconds: Double = 0.010
    // Envelope spans this many time constants; exp(-decayTimeconstants) gives residual amplitude at click end.
    private static let decayTimeconstants: Double = 5
    private static let decayTau: Double = clickSeconds / decayTimeconstants

    static func clickDuration(sampleRate: Double) -> Int {
        Int(clickSeconds * sampleRate)
    }

    static func render(
        into buffer: UnsafeMutablePointer<Float>,
        startFrame: Int,
        clickPhase: Int,
        count: Int,
        sampleRate: Double
    ) {
        guard count > 0 else { return }
        let twoPiF = 2.0 * Double.pi * frequency
        for idx in 0..<count {
            let sampleTime = Double(clickPhase + idx) / sampleRate
            let amplitude = exp(-sampleTime / decayTau)
            buffer[startFrame + idx] += Float(sin(twoPiF * sampleTime) * amplitude)
        }
    }
}

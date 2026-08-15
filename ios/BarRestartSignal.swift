/// Carries "the next audible buffer starts a new bar" from whoever paused to the
/// audio render thread.
///
/// Deliberately not "the render callback notices that it was paused": iOS stops the
/// `AVAudioEngine` when the audio session is interrupted, so during the pause that
/// matters most — a phone call — the callback is not running to notice anything.
/// Anything recorded only there is never recorded at all, and an auto-resume after a
/// call would re-enter on an arbitrary beat instead of the downbeat.
///
/// A pair of counters rather than one flag, so each is written by exactly one thread:
/// [raise] from the thread that pauses, [consume] from the render thread. A single
/// shared flag can lose a pause raised in the same buffer that consumed the previous
/// restart. A class rather than a struct so the two never take overlapping exclusive
/// access to one value.
final class BarRestartSignal {

    /// Written only by the thread that pauses.
    private var raised = 0
    /// Written only by the render thread.
    private var consumed = 0

    /// Records a pause, whatever caused it. Safe to call while the render callback is
    /// stopped, which is the whole point.
    func raise() {
        raised &+= 1
    }

    /// Called from the render callback on an audible buffer. True exactly once after
    /// each pause, and it is the caller's cue to reset the beat scheduler.
    func consume() -> Bool {
        guard consumed != raised else { return false }
        consumed = raised
        return true
    }

    /// A freshly opened stream begins on the downbeat by construction, so nothing is
    /// owed. Called only while no render callback is running.
    func clear() {
        raised = 0
        consumed = 0
    }
}

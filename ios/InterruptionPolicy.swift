/// What an `AVAudioSession` interruption should do to playback.
enum InterruptionOutcome {
    case pause
    case resume
    /// Leave playback exactly as it is.
    case ignore
}

enum InterruptionPhase {
    case began
    case ended
}

/// The interruption rules, kept as data so they can be asserted directly — the
/// engine that applies them owns an `AVAudioEngine` and an audio session, neither
/// of which exists in a unit test.
///
/// iOS is not symmetrical with Android here. Android distinguishes a transient
/// focus loss from a permanent one and stops on the latter; iOS reports one kind of
/// interruption and answers the question "should playback come back?" only when it
/// ends, through `.shouldResume`.
enum InterruptionPolicy {

    /// - Parameters:
    ///   - phase: whether the interruption is starting or over.
    ///   - shouldResume: the `.shouldResume` option on an `.ended` notification.
    ///   - pausedByInterruption: whether the pause now in effect was caused by an
    ///     interruption, as opposed to being asked for by the user.
    static func outcome(
        phase: InterruptionPhase,
        shouldResume: Bool,
        pausedByInterruption: Bool
    ) -> InterruptionOutcome {
        switch phase {
        // A call is not the end of the practice session, so pause rather than stop.
        case .began:
            return .pause

        case .ended:
            // The user's own pause is theirs to undo. Resuming it here would restart
            // the metronome in their pocket after a call they took on purpose.
            guard pausedByInterruption else { return .ignore }
            // Without `.shouldResume` iOS is explicitly telling us not to — typically
            // the user started another player after the call. Honour it.
            return shouldResume ? .resume : .ignore
        }
    }
}

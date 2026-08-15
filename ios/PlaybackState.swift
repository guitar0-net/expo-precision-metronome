/// Mirrors `PlaybackState` in TypeScript — the raw values go over the bridge, so
/// the two must not drift apart.
///
/// `paused` keeps `AVAudioEngine` and the audio session alive and only silences the
/// render callback; `stopped` is the one state in which nothing is allocated.
enum PlaybackState: String {
    case running
    case paused
    case stopped
}

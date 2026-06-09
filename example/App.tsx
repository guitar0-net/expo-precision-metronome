import { useEvent } from 'expo';
import ExpoPrecisionMetronomeModule, {
  BEAT_PATTERN_MAX_LENGTH,
  BeatAccent,
  BPM_MAX,
  BPM_MIN,
  DEFAULT_BEAT_PATTERN,
  setPattern,
  SOUND_PRESETS,
  SoundPreset,
  setBpm as setEngineBpm,
  setSound,
  start,
  stop,
} from 'expo-precision-metronome';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const PATTERN_PRESETS: { label: string; pattern: readonly BeatAccent[] }[] = [
  { label: '4/4', pattern: ['strong', 'normal', 'normal', 'normal'] },
  { label: '3/4', pattern: ['strong', 'normal', 'normal'] },
  { label: '6/8', pattern: ['strong', 'muted', 'muted', 'normal', 'muted', 'muted'] },
  { label: '2/4', pattern: ['strong', 'normal'] },
];

const ACCENT_CYCLE: BeatAccent[] = ['strong', 'normal', 'muted'];

const ACCENT_COLOR: Record<BeatAccent, string> = {
  strong: '#f59e0b',
  normal: '#4a90d9',
  muted: '#333355',
};

export default function App() {
  const [bpm, setBpm] = useState(120);
  const [isPlaying, setIsPlaying] = useState(false);
  const [sound, setSoundState] = useState<SoundPreset>('click');
  const [pattern, setPatternState] = useState<BeatAccent[]>([...DEFAULT_BEAT_PATTERN]);
  const [presetIndex, setPresetIndex] = useState<number | null>(0);

  const stopPayload = useEvent(ExpoPrecisionMetronomeModule, 'onStop');
  const beatPayload = useEvent(ExpoPrecisionMetronomeModule, 'onBeat');

  useEffect(() => {
    if (!stopPayload) return;
    setIsPlaying(false);
  }, [stopPayload]);

  const activeBeat = beatPayload ? beatPayload.beat % pattern.length : -1;

  const applyPattern = (next: BeatAccent[]) => {
    setPatternState(next);
    if (isPlaying) setPattern(next).catch(console.error);
  };

  const handlePlay = () => {
    setPattern(pattern)
      .then(() => start(bpm))
      .catch(console.error);
    setIsPlaying(true);
  };

  const handleStop = () => {
    stop();
    setIsPlaying(false);
  };

  const handleBpmChange = (delta: number) => {
    const next = Math.max(BPM_MIN, Math.min(BPM_MAX, bpm + delta));
    setBpm(next);
    if (isPlaying) setEngineBpm(next).catch(console.error);
  };

  const handleSoundChange = (preset: SoundPreset) => {
    setSoundState(preset);
    setSound(preset).catch(console.error);
  };

  const handlePresetSelect = (i: number) => {
    setPresetIndex(i);
    applyPattern([...PATTERN_PRESETS[i].pattern]);
  };

  const handleBeatTap = (i: number) => {
    const next = [...pattern];
    next[i] = ACCENT_CYCLE[(ACCENT_CYCLE.indexOf(next[i]) + 1) % ACCENT_CYCLE.length];
    setPresetIndex(null);
    applyPattern(next);
  };

  const handleAddBeat = () => {
    if (pattern.length >= BEAT_PATTERN_MAX_LENGTH) return;
    setPresetIndex(null);
    applyPattern([...pattern, 'normal']);
  };

  const handleRemoveBeat = () => {
    if (pattern.length <= 1) return;
    setPresetIndex(null);
    applyPattern(pattern.slice(0, -1));
  };

  const beatLabel = beatPayload ? `Beat ${beatPayload.beat + 1} · ${beatPayload.accent}` : '—';
  const statusText = isPlaying ? `♩ ${bpm} BPM · ${beatLabel}` : `${bpm} BPM`;

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Precision Metronome</Text>

        <Text style={styles.status}>{statusText}</Text>

        {/* BPM */}
        <View style={styles.bpmRow}>
          <TouchableOpacity
            style={[styles.bpmButton, bpm <= BPM_MIN && styles.disabled]}
            onPress={() => handleBpmChange(-1)}
            disabled={bpm <= BPM_MIN}>
            <Text style={styles.bpmButtonText}>−</Text>
          </TouchableOpacity>
          <Text style={styles.bpmDisplay}>{bpm}</Text>
          <TouchableOpacity
            style={[styles.bpmButton, bpm >= BPM_MAX && styles.disabled]}
            onPress={() => handleBpmChange(1)}
            disabled={bpm >= BPM_MAX}>
            <Text style={styles.bpmButtonText}>+</Text>
          </TouchableOpacity>
        </View>

        {/* Sound */}
        <Text style={styles.sectionLabel}>Sound</Text>
        <View style={styles.soundGrid}>
          {SOUND_PRESETS.map((preset) => (
            <TouchableOpacity
              key={preset}
              style={[styles.soundButton, sound === preset && styles.soundButtonActive]}
              onPress={() => handleSoundChange(preset)}>
              <Text
                style={[styles.soundButtonText, sound === preset && styles.soundButtonTextActive]}>
                {preset}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Pattern presets */}
        <Text style={styles.sectionLabel}>Pattern</Text>
        <View style={styles.presetRow}>
          {PATTERN_PRESETS.map((p, i) => (
            <TouchableOpacity
              key={p.label}
              style={[styles.presetButton, presetIndex === i && styles.presetButtonActive]}
              onPress={() => handlePresetSelect(i)}>
              <Text
                style={[
                  styles.presetButtonText,
                  presetIndex === i && styles.presetButtonTextActive,
                ]}>
                {p.label}
              </Text>
            </TouchableOpacity>
          ))}
          <TouchableOpacity
            style={[styles.presetButton, presetIndex === null && styles.presetButtonActive]}
            onPress={() => setPresetIndex(null)}>
            <Text
              style={[
                styles.presetButtonText,
                presetIndex === null && styles.presetButtonTextActive,
              ]}>
              Custom
            </Text>
          </TouchableOpacity>
        </View>

        {/* Beat grid */}
        <View style={styles.beatGrid}>
          {pattern.map((accent, i) => (
            <TouchableOpacity
              key={i}
              style={[
                styles.beatDot,
                { backgroundColor: ACCENT_COLOR[accent] },
                activeBeat === i && styles.beatDotActive,
              ]}
              onPress={() => handleBeatTap(i)}>
              <Text style={styles.beatDotText}>{accent[0].toUpperCase()}</Text>
            </TouchableOpacity>
          ))}
          {pattern.length < BEAT_PATTERN_MAX_LENGTH && (
            <TouchableOpacity style={styles.beatMutate} onPress={handleAddBeat}>
              <Text style={styles.beatMutateText}>+</Text>
            </TouchableOpacity>
          )}
          {pattern.length > 1 && (
            <TouchableOpacity style={styles.beatMutate} onPress={handleRemoveBeat}>
              <Text style={styles.beatMutateText}>−</Text>
            </TouchableOpacity>
          )}
        </View>
        <Text style={styles.patternHint}>Tap a beat to cycle: S strong · N normal · M muted</Text>

        {/* Play / Stop */}
        <View style={styles.controlRow}>
          <TouchableOpacity
            style={[styles.controlButton, styles.playButton, isPlaying && styles.disabled]}
            onPress={handlePlay}
            disabled={isPlaying}>
            <Text style={styles.controlButtonText}>Play</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.controlButton, styles.stopButton, !isPlaying && styles.disabled]}
            onPress={handleStop}
            disabled={!isPlaying}>
            <Text style={styles.controlButtonText}>Stop</Text>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

const BEAT_DOT_SIZE = 44;

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#1a1a2e',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    paddingTop: 80,
    paddingBottom: 40,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#e0e0e0',
    marginBottom: 40,
  },
  status: {
    fontSize: 16,
    color: '#a0a0c0',
    marginBottom: 40,
    textAlign: 'center',
  },
  bpmRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 40,
    gap: 24,
  },
  bpmButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#2a2a4a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bpmButtonText: {
    fontSize: 28,
    color: '#e0e0e0',
    lineHeight: 32,
  },
  bpmDisplay: {
    fontSize: 48,
    fontWeight: '700',
    color: '#e0e0e0',
    width: 100,
    textAlign: 'center',
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6060a0',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    alignSelf: 'flex-start',
    marginBottom: 12,
  },
  soundGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 40,
    justifyContent: 'flex-start',
    alignSelf: 'stretch',
  },
  soundButton: {
    paddingVertical: 10,
    paddingHorizontal: 18,
    borderRadius: 8,
    backgroundColor: '#2a2a4a',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  soundButtonActive: {
    backgroundColor: '#1e3a5f',
    borderColor: '#4a90d9',
  },
  soundButtonText: {
    fontSize: 15,
    color: '#a0a0c0',
    fontWeight: '500',
  },
  soundButtonTextActive: {
    color: '#e0e0e0',
    fontWeight: '700',
  },
  presetRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
    alignSelf: 'stretch',
    flexWrap: 'wrap',
  },
  presetButton: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#2a2a4a',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  presetButtonActive: {
    backgroundColor: '#1e3a5f',
    borderColor: '#4a90d9',
  },
  presetButtonText: {
    fontSize: 14,
    color: '#a0a0c0',
    fontWeight: '500',
  },
  presetButtonTextActive: {
    color: '#e0e0e0',
    fontWeight: '700',
  },
  beatGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    alignSelf: 'stretch',
    marginBottom: 8,
  },
  beatDot: {
    width: BEAT_DOT_SIZE,
    height: BEAT_DOT_SIZE,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  beatDotActive: {
    borderColor: '#ffffff',
    transform: [{ scale: 1.1 }],
  },
  beatDotText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#fff',
  },
  beatMutate: {
    width: BEAT_DOT_SIZE,
    height: BEAT_DOT_SIZE,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#4040607f',
    borderStyle: 'dashed',
  },
  beatMutateText: {
    fontSize: 20,
    color: '#6060a0',
    lineHeight: 24,
  },
  patternHint: {
    fontSize: 12,
    color: '#505070',
    alignSelf: 'flex-start',
    marginBottom: 40,
  },
  controlRow: {
    flexDirection: 'row',
    gap: 16,
  },
  controlButton: {
    paddingVertical: 14,
    paddingHorizontal: 36,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playButton: {
    backgroundColor: '#16a34a',
  },
  stopButton: {
    backgroundColor: '#dc2626',
  },
  controlButtonText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
  },
  disabled: {
    opacity: 0.4,
  },
});

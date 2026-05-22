import { useEvent } from 'expo';
import ExpoPrecisionMetronomeModule, {
  BPM_MAX,
  BPM_MIN,
  setBpm as setEngineBpm,
  start,
  stop,
} from 'expo-precision-metronome';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

export default function App() {
  const [bpm, setBpm] = useState(120);
  const [isPlaying, setIsPlaying] = useState(false);

  const stopPayload = useEvent(ExpoPrecisionMetronomeModule, 'onStop');

  useEffect(() => {
    if (!stopPayload) return;
    setIsPlaying(false);
  }, [stopPayload]);

  const handlePlay = () => {
    start(bpm);
    setIsPlaying(true);
  };

  const handleStop = () => {
    stop();
    setIsPlaying(false);
  };

  const handleBpmChange = (delta: number) => {
    const newBpm = Math.max(BPM_MIN, Math.min(BPM_MAX, bpm + delta));
    setBpm(newBpm);
    if (isPlaying) {
      setEngineBpm(newBpm).catch(console.error);
    }
  };

  const statusText = isPlaying ? `Playing at ${bpm} BPM` : 'Stopped';

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Precision Metronome</Text>

        <Text style={styles.status}>{statusText}</Text>

        <View style={styles.bpmRow}>
          <TouchableOpacity
            style={[styles.bpmButton, bpm <= BPM_MIN && styles.disabled]}
            onPress={() => handleBpmChange(-1)}
            disabled={bpm <= BPM_MIN}
          >
            <Text style={styles.bpmButtonText}>−</Text>
          </TouchableOpacity>

          <Text style={styles.bpmDisplay}>{bpm}</Text>

          <TouchableOpacity
            style={[styles.bpmButton, bpm >= BPM_MAX && styles.disabled]}
            onPress={() => handleBpmChange(1)}
            disabled={bpm >= BPM_MAX}
          >
            <Text style={styles.bpmButtonText}>+</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.controlRow}>
          <TouchableOpacity
            style={[styles.controlButton, styles.playButton, isPlaying && styles.disabled]}
            onPress={handlePlay}
            disabled={isPlaying}
          >
            <Text style={styles.controlButtonText}>Play</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.controlButton, styles.stopButton, !isPlaying && styles.disabled]}
            onPress={handleStop}
            disabled={!isPlaying}
          >
            <Text style={styles.controlButtonText}>Stop</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a2e',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#e0e0e0',
    marginBottom: 40,
  },
  status: {
    fontSize: 18,
    color: '#a0a0c0',
    marginBottom: 40,
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

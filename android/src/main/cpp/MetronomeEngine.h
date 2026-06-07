#pragma once
#include <oboe/Oboe.h>
#include <atomic>
#include <jni.h>
#include <memory>
#include "BeatScheduler.h"
#include "ClickSynthesizer.h"

class MetronomeEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    MetronomeEngine(JavaVM* jvm, jobject kotlinBridge);
    ~MetronomeEngine();

    // Safe to call while running — updates BPM in-place.
    void start(double bpm);
    // Updates BPM without touching the stream. No-op if not running.
    void setBpm(double bpm);
    // Updates sound preset without touching the stream. Takes effect on the next beat onset.
    void setSound(int presetIndex);
    // Idempotent.
    void stop();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    // Fires after a device disconnect or other unrecoverable stream error.
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void openStream();
    void closeStream();

    JavaVM* jvm_;
    jobject kotlinBridge_; // global JNI ref
    jmethodID onBeatMethodId_ = nullptr;
    jmethodID onStopMethodId_ = nullptr;

    std::shared_ptr<oboe::AudioStream> stream_;
    BeatScheduler scheduler_;

    // Written from the JS/Kotlin thread, read from the audio thread.
    // std::atomic load/store is lock-free on ARM64 (hardware atomic).
    std::atomic<double> currentBpm_{120.0};
    std::atomic<int>    currentPreset_{0};  // SoundPreset int value

    int64_t currentSample_ = 0;
    double sampleRate_ = 44100.0;
    // Duration and preset captured at click onset; audio-thread-only.
    int clickDurationSamples_ = 0;
    SoundPreset clickPreset_ = SoundPreset::Click;
    int clickPhase_ = -1; // -1 = no active click, >=0 = samples written so far
    std::atomic<bool> running_{false};
};

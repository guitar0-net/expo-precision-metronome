#include "MetronomeEngine.h"
#include "ClickSynthesizer.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <mutex>
#include <pthread.h>

static pthread_key_t sJvmDetachKey;
static std::once_flag sJvmDetachKeyOnce;

static void detachJvmThread(void* jvm) {
    reinterpret_cast<JavaVM*>(jvm)->DetachCurrentThread();
}

#define LOG_TAG "MetronomeEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MetronomeEngine::MetronomeEngine(JavaVM* jvm, jobject kotlinBridge)
    : jvm_(jvm) {
    JNIEnv* env;
    jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    kotlinBridge_ = env->NewGlobalRef(kotlinBridge);
    jclass cls = env->GetObjectClass(kotlinBridge_);
    onBeatMethodId_ = env->GetMethodID(cls, "onBeat", "(ID)V");
    onStopMethodId_ = env->GetMethodID(cls, "onNativeStop", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
}

MetronomeEngine::~MetronomeEngine() {
    closeStream();
    JNIEnv* env;
    if (jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && kotlinBridge_) {
        env->DeleteGlobalRef(kotlinBridge_);
        kotlinBridge_ = nullptr;
    }
}

void MetronomeEngine::start(double bpm) {
    currentBpm_.store(bpm, std::memory_order_relaxed);
    if (running_) return;
    openStream();
}

void MetronomeEngine::stop() {
    closeStream();
}

void MetronomeEngine::setBpm(double bpm) {
    currentBpm_.store(bpm, std::memory_order_relaxed);
}

void MetronomeEngine::setSound(int presetIndex) {
    currentPreset_.store(presetIndex, std::memory_order_relaxed);
}

void MetronomeEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    oboe::Result result = builder
        .setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(1)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->openStream(stream_);

    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return;
    }

    sampleRate_ = static_cast<double>(stream_->getSampleRate());
    currentSample_ = 0;
    clickPhase_ = -1;
    scheduler_.reset();

    result = stream_->start();
    if (result != oboe::Result::OK) {
        LOGE("stream start failed: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return;
    }
    running_ = true;
}

void MetronomeEngine::closeStream() {
    if (!running_.exchange(false)) return;
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

oboe::DataCallbackResult MetronomeEngine::onAudioReady(
    oboe::AudioStream* /*stream*/,
    void* audioData,
    int32_t numFrames) {

    auto* buffer = static_cast<float*>(audioData);
    std::memset(buffer, 0, sizeof(float) * static_cast<size_t>(numFrames));

    const double bpm = currentBpm_.load(std::memory_order_relaxed);
    const int64_t bufferStart = currentSample_;

    // Continue a click that started in a previous buffer.
    if (clickPhase_ >= 0) {
        int remaining = clickDurationSamples_ - clickPhase_;
        int toWrite = std::min(remaining, static_cast<int>(numFrames));
        ClickSynthesizer::render(buffer, 0, clickPhase_, toWrite, sampleRate_, clickPreset_);
        clickPhase_ += toWrite;
        if (clickPhase_ >= clickDurationSamples_) clickPhase_ = -1;
    }

    auto beat = scheduler_.nextBeat(static_cast<int>(numFrames), bufferStart, bpm, sampleRate_);
    if (beat.offset >= 0) {
        SoundPreset preset = static_cast<SoundPreset>(currentPreset_.load(std::memory_order_relaxed));
        int dur = ClickSynthesizer::clickDuration(sampleRate_, preset);
        int toWrite = std::min(dur, static_cast<int>(numFrames) - beat.offset);
        ClickSynthesizer::render(buffer, beat.offset, 0, toWrite, sampleRate_, preset);
        clickDurationSamples_ = dur;
        clickPreset_ = preset;
        clickPhase_ = (toWrite < dur) ? toWrite : -1;

        // Audio-clock timestamp: seconds elapsed since stream open.
        double beatTimestamp = static_cast<double>(bufferStart + beat.offset) / sampleRate_;

        // Dispatch to Kotlin on the audio thread. The JVM call is fast (< 1 µs)
        // and fires at most 5 times/s, so priority inversion risk is negligible.
        JNIEnv* env = nullptr;
        int getEnvStatus = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (getEnvStatus == JNI_EDETACHED) {
            // Register a pthread destructor once so the thread is detached when Oboe
            // destroys it (e.g. on device change), preventing a JNI resource leak.
            std::call_once(sJvmDetachKeyOnce, [] {
                pthread_key_create(&sJvmDetachKey, detachJvmThread);
            });
            jvm_->AttachCurrentThreadAsDaemon(&env, nullptr);
            pthread_setspecific(sJvmDetachKey, jvm_);
        }
        if (env && onBeatMethodId_ && kotlinBridge_) {
            env->CallVoidMethod(
                kotlinBridge_,
                onBeatMethodId_,
                static_cast<jint>(beat.beatNumber),
                static_cast<jdouble>(beatTimestamp));
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
    }

    currentSample_ += numFrames;
    return oboe::DataCallbackResult::Continue;
}

void MetronomeEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    // Use exchange so a concurrent explicit stop() wins and we don't double-notify.
    if (!running_.exchange(false)) return;

    JNIEnv* env = nullptr;
    bool attached = false;
    if (jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        jvm_->AttachCurrentThreadAsDaemon(&env, nullptr);
        attached = true;
    }
    if (env && onStopMethodId_ && kotlinBridge_) {
        jstring reason = env->NewStringUTF("interruption");
        if (reason) {
            env->CallVoidMethod(kotlinBridge_, onStopMethodId_, reason);
            env->DeleteLocalRef(reason);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    // Explicit detach is safe here: onErrorAfterClose fires exactly once per stream
    // lifetime (the stream is permanently closed at this point), so this thread is
    // never reused by Oboe. The pthread_key auto-detach used in onAudioReady is
    // unnecessary for this one-shot callback.
    if (attached) jvm_->DetachCurrentThread();
}

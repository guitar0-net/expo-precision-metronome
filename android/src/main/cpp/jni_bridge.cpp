#include <jni.h>
#include "MetronomeEngine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeCreate(JNIEnv* env, jobject thiz) {
    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    return reinterpret_cast<jlong>(new MetronomeEngine(jvm, thiz));
}

JNIEXPORT void JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeDestroy(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete reinterpret_cast<MetronomeEngine*>(handle);
}

JNIEXPORT void JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeStart(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jdouble bpm) {
    reinterpret_cast<MetronomeEngine*>(handle)->start(bpm);
}

JNIEXPORT void JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeStop(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    reinterpret_cast<MetronomeEngine*>(handle)->stop();
}

JNIEXPORT void JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeSetBpm(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jdouble bpm) {
    reinterpret_cast<MetronomeEngine*>(handle)->setBpm(bpm);
}

JNIEXPORT void JNICALL
Java_net_guitar0_metronome_MetronomeEngine_nativeSetSound(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint presetIndex) {
    reinterpret_cast<MetronomeEngine*>(handle)->setSound(static_cast<int>(presetIndex));
}

} // extern "C"

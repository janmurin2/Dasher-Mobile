#include "AndroidDasherInterface.h"

#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "DasherJNI"

static inline AndroidDasherInterface *fromHandle(jlong h) {
    return reinterpret_cast<AndroidDasherInterface *>(static_cast<uintptr_t>(h));
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF("Hello world!");
}

JNIEXPORT jlong JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeCreate(JNIEnv *env, jclass, jstring filesDir) {
    const char *dir = env->GetStringUTFChars(filesDir, nullptr);
    if (!dir) return 0L;
    std::string dirStr(dir);
    env->ReleaseStringUTFChars(filesDir, dir);
    auto *iface = new AndroidDasherInterface(dirStr);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "nativeCreate handle=%p", static_cast<void *>(iface));
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(iface));
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeSetScreenSize(JNIEnv *, jclass, jlong handle,
                                                                  jint width, jint height) {
    if (!fromHandle(handle)) return;
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "setScreenSize %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeFrame(JNIEnv *, jclass, jlong handle, jlong) {
    (void)fromHandle(handle);
}

}

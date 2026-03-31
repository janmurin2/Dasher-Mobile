#include "AndroidDasherInterface.h"

#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#define LOG_TAG "DasherJNI"

namespace DasherAndroid {
void SetAssetManager(AAssetManager *assetManager);
void SetUserDataDir(const std::string &userDataDir);
}

namespace {

struct NativeSession {
    std::unique_ptr<AndroidDasherInterface> iface;
    int width = 0;
    int height = 0;
    float touchX = 0.0f;
    float touchY = 0.0f;
    bool touchActive = false;

    explicit NativeSession(const std::string &filesDir)
        : iface(std::make_unique<AndroidDasherInterface>(filesDir)) {}
};

static inline NativeSession *fromHandle(jlong handle) {
    return reinterpret_cast<NativeSession *>(static_cast<uintptr_t>(handle));
}

static inline jlong toHandle(NativeSession *session) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(session));
}

static jintArray toJIntArray(JNIEnv *env, const std::vector<jint> &data) {
    jintArray result = env->NewIntArray(static_cast<jsize>(data.size()));
    if (!result || data.empty()) {
        return result;
    }
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(data.size()), data.data());
    return result;
}

static bool hasVisibleBoxCommands(const std::vector<jint> &commands) {
    for (size_t i = 0; i + 5 < commands.size(); i += 6) {
        const int op = commands[i];
        if (op == 3 || op == 4) {
            const int a = commands[i + 1];
            const int b = commands[i + 2];
            const int c = commands[i + 3];
            const int d = commands[i + 4];
            const int color = commands[i + 5];
            const int alpha = (color >> 24) & 0xFF;
            if (alpha < 24) {
                continue;
            }
            if (a == c || b == d) {
                continue;
            }
            return true;
        }
    }
    return false;
}

static void pushCommand(std::vector<jint> &commands,
                        jint op,
                        jint a,
                        jint b,
                        jint c,
                        jint d,
                        jint color) {
    commands.push_back(op);
    commands.push_back(a);
    commands.push_back(b);
    commands.push_back(c);
    commands.push_back(d);
    commands.push_back(color);
}

static void appendFallbackBoxes(NativeSession &session, std::vector<jint> &commands) {
    if (session.width <= 0 || session.height <= 0) {
        return;
    }

    const int w = session.width;
    const int h = session.height;
    const int midY = h / 2;
    const int left = w / 20;
    const int right = w - left;

    pushCommand(commands, 4, left, h / 8, right, midY - h / 20, static_cast<jint>(0xFF1B5E20));
    pushCommand(commands, 3, left, h / 8, right, midY - h / 20, static_cast<jint>(0xFF81C784));

    pushCommand(commands, 4, left, midY + h / 20, right, h - h / 8, static_cast<jint>(0xFF0D47A1));
    pushCommand(commands, 3, left, midY + h / 20, right, h - h / 8, static_cast<jint>(0xFF90CAF9));

    const int px = session.touchActive ? static_cast<int>(session.touchX) : (w / 2);
    const int py = session.touchActive ? static_cast<int>(session.touchY) : (h / 2);
    const int marker = std::max(10, std::min(w, h) / 45);

    pushCommand(commands, 1, px, py, marker, 1, static_cast<jint>(0xFFD50000));
    pushCommand(commands, 2, px - marker * 2, py, px + marker * 2, py, static_cast<jint>(0xFFFFCDD2));
    pushCommand(commands, 2, px, py - marker * 2, px, py + marker * 2, static_cast<jint>(0xFFFFCDD2));
}

static void logFrameDiagnostics(const std::vector<jint> &commands) {
    static int frameCounter = 0;
    frameCounter++;
    if (frameCounter % 120 != 0) return;

    if (commands.empty()) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "frame commands empty");
        return;
    }

    int minX = INT32_MAX;
    int minY = INT32_MAX;
    int maxX = INT32_MIN;
    int maxY = INT32_MIN;

    const int total = static_cast<int>(commands.size() / 6);
    const int preview = std::min(total, 4);
    for (int i = 0; i < total; ++i) {
        const int base = i * 6;
        const int op = commands[base];
        const int a = commands[base + 1];
        const int b = commands[base + 2];
        const int c = commands[base + 3];
        const int d = commands[base + 4];
        if (op == 1) {
            minX = std::min(minX, a - c);
            maxX = std::max(maxX, a + c);
            minY = std::min(minY, b - c);
            maxY = std::max(maxY, b + c);
        } else if (op == 2 || op == 3 || op == 4) {
            minX = std::min(minX, std::min(a, c));
            maxX = std::max(maxX, std::max(a, c));
            minY = std::min(minY, std::min(b, d));
            maxY = std::max(maxY, std::max(b, d));
        }
        if (i < preview) {
            __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
                                "op[%d]=%d a=%d b=%d c=%d d=%d",
                                i, op, a, b, c, d);
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "cmds=%d bounds=[%d,%d]-[%d,%d]",
                        total, minX, minY, maxX, maxY);
}

}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF("DasherCore 1.0");
}

JNIEXPORT jlong JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeCreate(JNIEnv *env, jclass, jstring filesDir) {
    const char *dir = env->GetStringUTFChars(filesDir, nullptr);
    if (!dir) {
        return 0L;
    }
    std::string dirStr(dir);
    env->ReleaseStringUTFChars(filesDir, dir);

    auto *session = new NativeSession(dirStr);
    DasherAndroid::SetUserDataDir(dirStr);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "nativeCreate handle=%p", static_cast<void *>(session));
    return toHandle(session);
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    auto *session = fromHandle(handle);
    delete session;
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeSetAssetManager(JNIEnv *env,
                                                                    jclass,
                                                                    jlong handle,
                                                                    jobject assetManager) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface || !assetManager) {
        return;
    }
    AAssetManager *nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    if (!nativeAssetManager) {
        return;
    }
    DasherAndroid::SetAssetManager(nativeAssetManager);
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeSetScreenSize(JNIEnv *,
                                                                  jclass,
                                                                  jlong handle,
                                                                  jint width,
                                                                  jint height) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return;
    }
    session->width = std::max(0, static_cast<int>(width));
    session->height = std::max(0, static_cast<int>(height));
    session->iface->SetScreenSize(static_cast<int>(width), static_cast<int>(height));
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeTouch(JNIEnv *,
                                                          jclass,
                                                          jlong handle,
                                                          jint action,
                                                          jfloat x,
                                                          jfloat y) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return;
    }
    session->touchX = static_cast<float>(x);
    session->touchY = static_cast<float>(y);
    if (action == 0) {
        session->touchActive = true;
    } else if (action == 2) {
        session->touchActive = false;
    }
    session->iface->SetTouch(static_cast<int>(action), static_cast<float>(x), static_cast<float>(y));
}

JNIEXPORT jstring JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeGetAlphabetId(JNIEnv *env,
                                                                  jclass,
                                                                  jlong handle) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(session->iface->GetAlphabetId().c_str());
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeSetAlphabetId(JNIEnv *env,
                                                                  jclass,
                                                                  jlong handle,
                                                                  jstring alphabetId) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface || !alphabetId) {
        return;
    }
    const char *rawAlphabetId = env->GetStringUTFChars(alphabetId, nullptr);
    if (!rawAlphabetId) {
        return;
    }
    session->iface->SetAlphabetId(rawAlphabetId);
    env->ReleaseStringUTFChars(alphabetId, rawAlphabetId);
}

JNIEXPORT jint JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeGetLanguageModelId(JNIEnv *,
                                                                       jclass,
                                                                       jlong handle) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return 0;
    }
    return static_cast<jint>(session->iface->GetLanguageModelId());
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeSetLanguageModelId(JNIEnv *,
                                                                       jclass,
                                                                       jlong handle,
                                                                       jint modelId) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return;
    }
    session->iface->SetLanguageModelId(static_cast<int>(modelId));
}

JNIEXPORT jintArray JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeFrame(JNIEnv *env,
                                                         jclass,
                                                         jlong handle,
                                                         jlong timeMs) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return env->NewIntArray(0);
    }

    const auto rawCommands = session->iface->Frame(static_cast<long>(timeMs));
    std::vector<jint> commands(rawCommands.begin(), rawCommands.end());
    if (!hasVisibleBoxCommands(commands)) {
        appendFallbackBoxes(*session, commands);
    }
    logFrameDiagnostics(commands);
    return toJIntArray(env, commands);
}

JNIEXPORT jobjectArray JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeGetFrameStrings(JNIEnv *env,
                                                                    jclass,
                                                                    jlong handle) {
    jclass strClass = env->FindClass("java/lang/String");
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return env->NewObjectArray(0, strClass, nullptr);
    }
    const auto strings = session->iface->TakeFrameStrings();

    static int s_logCounter = 0;
    if (++s_logCounter % 120 == 0) {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "frameStrings count=%zu", strings.size());
    }

    auto arr = env->NewObjectArray(static_cast<jsize>(strings.size()), strClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(strings.size()); ++i) {
        jstring jstr = env->NewStringUTF(strings[i].c_str());
        env->SetObjectArrayElement(arr, i, jstr);
        env->DeleteLocalRef(jstr);
    }
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeGetOutputText(JNIEnv *env,
                                                                  jclass,
                                                                  jlong handle) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(session->iface->GetOutputText().c_str());
}

JNIEXPORT void JNICALL
Java_com_janmurin_dashermobile_NativeBridge_nativeResetOutputText(JNIEnv *,
                                                                    jclass,
                                                                    jlong handle) {
    auto *session = fromHandle(handle);
    if (!session || !session->iface) {
        return;
    }
    session->iface->ResetOutputText();
}

}

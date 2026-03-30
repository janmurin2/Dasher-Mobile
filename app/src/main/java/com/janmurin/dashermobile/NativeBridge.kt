package com.janmurin.dashermobile

import android.content.res.AssetManager

class NativeBridge {
    companion object {
        init {
            System.loadLibrary("dasher_jni")
        }

        @JvmStatic external fun nativeVersion(): String
        @JvmStatic external fun nativeCreate(filesDir: String): Long
        @JvmStatic external fun nativeDestroy(handle: Long)
        @JvmStatic external fun nativeSetAssetManager(handle: Long, assetManager: AssetManager)
        @JvmStatic external fun nativeSetScreenSize(handle: Long, width: Int, height: Int)
        @JvmStatic external fun nativeTouch(handle: Long, action: Int, x: Float, y: Float)
        @JvmStatic external fun nativeFrame(handle: Long, timeMs: Long): IntArray
        @JvmStatic external fun nativeGetFrameStrings(handle: Long): Array<String>
        @JvmStatic external fun nativeGetOutputText(handle: Long): String
        @JvmStatic external fun nativeResetOutputText(handle: Long)
        @JvmStatic external fun nativeGetLanguageModelId(handle: Long): Int
        @JvmStatic external fun nativeSetLanguageModelId(handle: Long, modelId: Int)
    }
}

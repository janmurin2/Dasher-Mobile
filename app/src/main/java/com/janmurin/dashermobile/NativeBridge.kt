package com.janmurin.dashermobile

class NativeBridge {
    companion object {
        init {
            System.loadLibrary("dasher_jni")
        }

        @JvmStatic external fun nativeVersion(): String
        @JvmStatic external fun nativeCreate(filesDir: String): Long
        @JvmStatic external fun nativeDestroy(handle: Long)
        @JvmStatic external fun nativeSetScreenSize(handle: Long, width: Int, height: Int)
        @JvmStatic external fun nativeFrame(handle: Long, timeMs: Long)
    }
}

package com.janmurin.dashermobile

import android.content.res.AssetManager

/**
 * Thin Kotlin wrapper that exposes the DasherCore JNI library to the rest of the application.
 *
 * Every public method maps 1-to-1 onto a native function implemented in `jni_bridge.cpp`.
 * The native library (`dasher_jni`) is loaded automatically when the class is first referenced.
 *
 * All handle-based functions accept a [Long] that was previously returned by [nativeCreate].
 * Passing an invalid (e.g. already-destroyed) handle is safe: the native layer performs a null
 * check and is a no-op in that case.
 */
class NativeBridge {
    companion object {
        init {
            System.loadLibrary("dasher_jni")
        }

        /** Returns a human-readable version string identifying the bundled DasherCore build. */
        @JvmStatic external fun nativeVersion(): String

        /**
         * Allocates a new native DasherCore session.
         *
         * @param filesDir Absolute path to the application's private files directory.
         *   DasherCore writes its XML settings file here.
         * @return An opaque handle (non-zero on success, zero on failure) that must be
         *   passed to every subsequent native call and eventually to [nativeDestroy].
         */
        @JvmStatic external fun nativeCreate(filesDir: String): Long

        /**
         * Releases all native resources associated with [handle].
         *
         * After this call the handle is invalid and must not be used.
         *
         * @param handle Opaque session handle returned by [nativeCreate].
         */
        @JvmStatic external fun nativeDestroy(handle: Long)

        /**
         * Provides the Android [AssetManager] to the native layer so that DasherCore can read
         * alphabet XML files and other bundled assets from the APK.
         *
         * @param handle Opaque session handle.
         * @param assetManager The application's [AssetManager] instance.
         */
        @JvmStatic external fun nativeSetAssetManager(handle: Long, assetManager: AssetManager)

        /**
         * Notifies the native engine of the current rendering surface dimensions.
         *
         * Must be called whenever the canvas size changes (e.g. on first layout or rotation).
         *
         * @param handle Opaque session handle.
         * @param width  Surface width in pixels.
         * @param height Surface height in pixels.
         */
        @JvmStatic external fun nativeSetScreenSize(handle: Long, width: Int, height: Int)

        /**
         * Delivers a touch event to the DasherCore pointer-input module.
         *
         * @param handle Opaque session handle.
         * @param action Touch action code: `0` = DOWN, `1` = MOVE, `2` = UP/CANCEL.
         * @param x      Touch X coordinate in surface pixels.
         * @param y      Touch Y coordinate in surface pixels.
         */
        @JvmStatic external fun nativeTouch(handle: Long, action: Int, x: Float, y: Float)

        /**
         * Advances the Dasher animation by one frame and returns the resulting draw commands.
         *
         * The returned [IntArray] is a flat list of 6-integer draw-command records:
         * `[op, a, b, c, d, color]` where `op` identifies the primitive type:
         * - `0` – fill canvas with `color`
         * - `1` – circle at `(a, b)` with radius `c`; `d != 0` means filled
         * - `2` – line from `(a, b)` to `(c, d)`
         * - `3` – stroked rectangle from `(a, b)` to `(c, d)`
         * - `4` – filled rectangle from `(a, b)` to `(c, d)`
         *
         * @param handle  Opaque session handle.
         * @param timeMs  Current frame timestamp in milliseconds (typically from Choreographer).
         * @return Flat draw-command array; never null but may be empty.
         */
        @JvmStatic external fun nativeFrame(handle: Long, timeMs: Long): IntArray

        /**
         * Retrieves text strings (e.g. labels on Dasher nodes) that were produced during the
         * most recent [nativeFrame] call and removes them from the internal queue.
         *
         * @param handle Opaque session handle.
         * @return Array of strings indexed to match the text-draw commands in the last frame.
         */
        @JvmStatic external fun nativeGetFrameStrings(handle: Long): Array<String>

        /**
         * Returns the text that DasherCore has output so far in the current session.
         *
         * @param handle Opaque session handle.
         * @return The accumulated output string.
         */
        @JvmStatic external fun nativeGetOutputText(handle: Long): String

        /**
         * Clears the output-text buffer inside the native session.
         *
         * @param handle Opaque session handle.
         */
        @JvmStatic external fun nativeResetOutputText(handle: Long)

        /**
         * Returns the identifier of the currently active Dasher alphabet
         * (e.g. `"English with limited punctuation"`, `"Slovak"`).
         *
         * @param handle Opaque session handle.
         */
        @JvmStatic external fun nativeGetAlphabetId(handle: Long): String

        /**
         * Switches the Dasher alphabet.  The change takes effect on the next [nativeFrame] call.
         *
         * @param handle     Opaque session handle.
         * @param alphabetId Dasher alphabet identifier string.
         */
        @JvmStatic external fun nativeSetAlphabetId(handle: Long, alphabetId: String)

        /**
         * Returns the numeric ID of the active language model.
         *
         * @param handle Opaque session handle.
         * @return Language-model ID (see [LanguageModel]).
         */
        @JvmStatic external fun nativeGetLanguageModelId(handle: Long): Int

        /**
         * Switches the language model used by DasherCore.
         *
         * @param handle  Opaque session handle.
         * @param modelId Numeric language-model ID (see [LanguageModel]).
         */
        @JvmStatic external fun nativeSetLanguageModelId(handle: Long, modelId: Int)

        /**
         * Returns the movement-speed multiplier as a percentage (100 = normal speed).
         *
         * @param handle Opaque session handle.
         */
        @JvmStatic external fun nativeGetMovementSpeedPercent(handle: Long): Int

        /**
         * Sets the movement-speed multiplier.
         *
         * @param handle  Opaque session handle.
         * @param percent Speed percentage; valid values are 50–400.
         */
        @JvmStatic external fun nativeSetMovementSpeedPercent(handle: Long, percent: Int)
    }
}

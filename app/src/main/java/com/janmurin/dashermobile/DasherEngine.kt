package com.janmurin.dashermobile

import android.view.Choreographer

enum class InputMode {
    TOUCH,
    TILT
}

class DasherEngine(
    private val nativeHandle: Long,
    private val frameConsumer: (IntArray, Array<String>) -> Unit
) : Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var destroyed = false
    private var hasSurface = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var inputMode = InputMode.TOUCH
    private var tiltActive = false

    var onTextUpdate: ((String) -> Unit)? = null

    fun setInputMode(mode: InputMode) {
        if (destroyed || nativeHandle == 0L || inputMode == mode) return
        if (inputMode == InputMode.TILT && tiltActive) {
            NativeBridge.nativeTouch(nativeHandle, 2, currentX(), currentY())
            tiltActive = false
        }
        inputMode = mode
    }

    fun getInputMode(): InputMode = inputMode

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) return
        hasSurface = true
        surfaceWidth = width
        surfaceHeight = height
        NativeBridge.nativeSetScreenSize(nativeHandle, width, height)
    }

    fun onTouch(action: Int, x: Float, y: Float) {
        if (destroyed || nativeHandle == 0L || inputMode != InputMode.TOUCH) return
        NativeBridge.nativeTouch(nativeHandle, action, x, y)
    }

    fun onTiltNormalized(normalizedX: Float, normalizedY: Float) {
        if (destroyed || nativeHandle == 0L || inputMode != InputMode.TILT || !hasSurface) return

        val x = normalizedX.coerceIn(0f, 1f) * surfaceWidth.toFloat()
        val y = normalizedY.coerceIn(0f, 1f) * surfaceHeight.toFloat()

        if (!tiltActive) {
            NativeBridge.nativeTouch(nativeHandle, 0, x, y)
            tiltActive = true
        } else {
            NativeBridge.nativeTouch(nativeHandle, 1, x, y)
        }
    }

    fun clearTiltInput() {
        if (destroyed || nativeHandle == 0L || !tiltActive) return
        NativeBridge.nativeTouch(nativeHandle, 2, currentX(), currentY())
        tiltActive = false
    }

    fun start() {
        if (running || destroyed) return
        running = true
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)
    }

    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        if (nativeHandle != 0L) {
            NativeBridge.nativeDestroy(nativeHandle)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (!destroyed && nativeHandle != 0L && hasSurface) {
            val frameTimeMs = frameTimeNanos / 1_000_000L
            val commands = NativeBridge.nativeFrame(nativeHandle, frameTimeMs)
            val strings = NativeBridge.nativeGetFrameStrings(nativeHandle)
            frameConsumer(commands, strings)
            onTextUpdate?.invoke(NativeBridge.nativeGetOutputText(nativeHandle))
        }
        if (running) {
            choreographer.postFrameCallback(this)
        }
    }

    fun resetOutputText() {
        if (destroyed || nativeHandle == 0L) return
        NativeBridge.nativeResetOutputText(nativeHandle)
    }

    private fun currentX(): Float = surfaceWidth * 0.5f
    private fun currentY(): Float = surfaceHeight * 0.5f
}

package com.janmurin.dashermobile

import android.util.Log
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
    private var hasBootstrapFrame = false

    private var inputMode = InputMode.TOUCH
    private var isPaused = true
    private var isTouching = false
    private var tiltActive = false

    var onTextUpdate: ((String) -> Unit)? = null
    var onStatusUpdate: ((InputMode, Boolean) -> Unit)? = null

    private fun emitStatus() {
        onStatusUpdate?.invoke(inputMode, isPaused)
    }

    private fun pauseInternal() {
        if (isPaused) return
        if (inputMode == InputMode.TOUCH && isTouching) {
            if (!destroyed && nativeHandle != 0L) {
                NativeBridge.nativeTouch(nativeHandle, 2, currentX(), currentY())
            }
            isTouching = false
        }
        if (inputMode == InputMode.TILT && tiltActive) {
            if (!destroyed && nativeHandle != 0L) {
                NativeBridge.nativeTouch(nativeHandle, 2, currentX(), currentY())
            }
            tiltActive = false
        }
        isPaused = true
        emitStatus()
        Log.d("DasherEngine", "paused mode=$inputMode")
    }

    private fun resumeTouchIfNeeded() {
        if (!isPaused) return
        isPaused = false
        emitStatus()
    }

    fun setInputMode(mode: InputMode): Boolean {
        if (destroyed || nativeHandle == 0L || inputMode == mode) return true
        if (!isPaused) return false
        pauseInternal()
        inputMode = mode
        if (mode == InputMode.TILT) {
            isPaused = false
        }
        emitStatus()
        Log.d("DasherEngine", "inputMode -> $mode isPaused=$isPaused")
        return true
    }

    fun getInputMode(): InputMode = inputMode
    fun isPaused(): Boolean = isPaused

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) return
        hasSurface = true
        surfaceWidth = width
        surfaceHeight = height
        hasBootstrapFrame = false
        NativeBridge.nativeSetScreenSize(nativeHandle, width, height)
    }

    fun onTouch(action: Int, x: Float, y: Float) {
        if (destroyed || nativeHandle == 0L) return
        when (inputMode) {
            InputMode.TOUCH -> when (action) {
                0 -> {
                    resumeTouchIfNeeded()
                    isTouching = true
                    NativeBridge.nativeTouch(nativeHandle, 0, x, y)
                }
                1 -> {
                    if (!isPaused && isTouching) {
                        NativeBridge.nativeTouch(nativeHandle, 1, x, y)
                    }
                }
                2 -> {
                    if (isTouching) {
                        NativeBridge.nativeTouch(nativeHandle, 2, x, y)
                        isTouching = false
                    }
                    pauseInternal()
                }
            }
            InputMode.TILT -> when (action) {
                0, 1, 2 -> pauseInternal()
            }
        }
    }

    fun onTiltNormalized(normalizedX: Float, normalizedY: Float) {
        if (destroyed || nativeHandle == 0L || inputMode != InputMode.TILT || !hasSurface || isPaused) return
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
        emitStatus()
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
        val shouldRenderFrame = !isPaused || !hasBootstrapFrame
        if (!destroyed && nativeHandle != 0L && hasSurface && shouldRenderFrame) {
            val frameTimeMs = frameTimeNanos / 1_000_000L
            val commands = NativeBridge.nativeFrame(nativeHandle, frameTimeMs)
            val strings = NativeBridge.nativeGetFrameStrings(nativeHandle)
            frameConsumer(commands, strings)
            onTextUpdate?.invoke(NativeBridge.nativeGetOutputText(nativeHandle))
            hasBootstrapFrame = true
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

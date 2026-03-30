package com.janmurin.dashermobile

import android.util.Log
import android.view.Choreographer

enum class InputMode {
    TOUCH,
    TILT
}

enum class LanguageModel(val id: Int) {
    PPM(0),
    WORD(2);

    companion object {
        fun fromId(id: Int): LanguageModel = if (id == WORD.id) WORD else PPM
    }
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
    private var lastRenderableCommands: IntArray = IntArray(0)
    private var lastRenderableStrings: Array<String> = emptyArray()

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

            val opCount = commands.size / 6
            val isRenderableFrame = opCount > 6 || strings.isNotEmpty()
            if (isRenderableFrame) {
                frameConsumer(commands, strings)
                lastRenderableCommands = commands
                lastRenderableStrings = strings
                hasBootstrapFrame = true
            } else if (lastRenderableCommands.isNotEmpty()) {
                frameConsumer(lastRenderableCommands, lastRenderableStrings)
                hasBootstrapFrame = !isPaused
            } else {
                frameConsumer(commands, strings)
                hasBootstrapFrame = !isPaused
            }

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

    fun getLanguageModel(): LanguageModel {
        if (destroyed || nativeHandle == 0L) return LanguageModel.PPM
        return LanguageModel.fromId(NativeBridge.nativeGetLanguageModelId(nativeHandle))
    }

    fun setLanguageModel(model: LanguageModel): Boolean {
        if (destroyed || nativeHandle == 0L) return false
        if (!isPaused) return false
        if (getLanguageModel() == model) return true
        NativeBridge.nativeSetLanguageModelId(nativeHandle, model.id)
        hasBootstrapFrame = false
        return true
    }

    private fun currentX(): Float = surfaceWidth * 0.5f
    private fun currentY(): Float = surfaceHeight * 0.5f
}

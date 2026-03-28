package com.janmurin.dashermobile

import android.view.Choreographer

class DasherEngine(
    private val nativeHandle: Long,
    private val frameConsumer: (IntArray) -> Unit
) : Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var destroyed = false
    private var hasSurface = false

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) return
        hasSurface = true
        NativeBridge.nativeSetScreenSize(nativeHandle, width, height)
    }

    fun onTouch(action: Int, x: Float, y: Float) {
        if (destroyed || nativeHandle == 0L) return
        NativeBridge.nativeTouch(nativeHandle, action, x, y)
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
            frameConsumer(commands)
        }
        if (running) {
            choreographer.postFrameCallback(this)
        }
    }
}


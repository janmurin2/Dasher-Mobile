package com.janmurin.dashermobile

import android.util.Log
import android.view.Choreographer

/**
 * Describes the physical input mechanism the user employs to drive the Dasher cursor.
 *
 * - [TOUCH] – the user drags their finger across the canvas.
 * - [TILT]  – the device's game-rotation-vector sensor steers the cursor.
 */
enum class InputMode {
    TOUCH,
    TILT
}

/**
 * The statistical language model used by DasherCore to assign letter probabilities.
 *
 * @property id Numeric identifier passed to [NativeBridge.nativeSetLanguageModelId].
 */
enum class LanguageModel(val id: Int) {
    /** Prediction by Partial Match – the default, self-learning model. */
    PPM(0),
    /** Word-based n-gram model. */
    WORD(2),
    /** KenLM binary model file loaded from the device's private files directory. */
    KENLM(5);

    companion object {
        /**
         * Returns the [LanguageModel] matching [id], falling back to [PPM] for unknown values.
         */
        fun fromId(id: Int): LanguageModel = when (id) {
            WORD.id -> WORD
            KENLM.id -> KENLM
            else -> PPM
        }
    }
}

/**
 * Maps a human-readable Dasher language to its alphabet identifier string and the serialised
 * preference value used in [DasherPrefs].
 *
 * @property alphabetId Alphabet identifier consumed by [NativeBridge.nativeSetAlphabetId].
 * @property preferenceValue Short locale tag persisted in SharedPreferences.
 */
enum class DasherLanguage(val alphabetId: String, val preferenceValue: String) {
    ENGLISH("English with limited punctuation", "en"),
    SLOVAK("Slovak", "sk");

    companion object {
        /**
         * Returns the [DasherLanguage] whose [alphabetId] matches [alphabetId],
         * or [ENGLISH] if no match is found.
         */
        fun fromAlphabetId(alphabetId: String): DasherLanguage {
            return values().firstOrNull { it.alphabetId == alphabetId } ?: ENGLISH
        }

        /**
         * Returns the [DasherLanguage] whose [preferenceValue] matches [value],
         * or [ENGLISH] if no match is found.
         */
        fun fromPreferenceValue(value: String?): DasherLanguage {
            return values().firstOrNull { it.preferenceValue == value } ?: ENGLISH
        }
    }
}

/**
 * Drives a single DasherCore native session frame-by-frame via Android's [Choreographer].
 *
 * Responsibilities:
 * - Scheduling per-vsync frame callbacks through [Choreographer].
 * - Translating touch / tilt events into [NativeBridge.nativeTouch] calls.
 * - Maintaining pause / resume semantics and broadcasting state changes via [onStatusUpdate].
 * - Forwarding rendered draw-commands and output text to registered consumers.
 *
 * Instances are created exclusively by [DasherSessionCoordinator] which also manages their
 * lifecycle.  All public methods **must** be called on the main thread.
 *
 * @param nativeHandle Opaque native session handle from [NativeBridge.nativeCreate].
 * @param frameConsumer Initial callback invoked on every rendered frame with the flat
 *   draw-command array and the accompanying string labels.
 */
class DasherEngine(
    private val nativeHandle: Long,
    frameConsumer: (IntArray, Array<String>) -> Unit
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

    /**
     * Callback invoked whenever the accumulated output text changes.
     * Called on the main thread during [doFrame].
     */
    var onTextUpdate: ((String) -> Unit)? = null

    /**
     * Callback invoked whenever the engine's [InputMode] or pause state changes.
     * Receives the current [InputMode] and whether the engine is paused.
     */
    var onStatusUpdate: ((InputMode, Boolean) -> Unit)? = null

    @Volatile
    private var frameConsumer: (IntArray, Array<String>) -> Unit = frameConsumer

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

    /**
     * Resumes the engine if it is currently paused.
     *
     * Has no effect when the engine is already running.
     */
    fun unpause() {
        if (!isPaused) return
        isPaused = false
        emitStatus()
    }

    /**
     * Attempts to switch the active [InputMode].
     *
     * The switch is only permitted while the engine is paused.  If the engine is currently
     * running when this is called it returns `false` and no change is made.
     *
     * @param mode The desired [InputMode].
     * @return `true` if the mode was changed (or was already set to [mode]), `false` otherwise.
     */
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

    /** Returns the currently active [InputMode]. */
    fun getInputMode(): InputMode = inputMode

    /** Returns `true` if the engine is currently paused (Dasher is not advancing). */
    fun isPaused(): Boolean = isPaused

    /**
     * Requests that the engine transition to the paused state.
     *
     * Any active touch or tilt input is cancelled before pausing.
     */
    fun requestPause() {
        pauseInternal()
    }

    /**
     * Notifies the engine that the rendering surface has changed size.
     *
     * This resets the bootstrap-frame flag so that a fresh frame is rendered at the new
     * resolution before the engine is allowed to go fully idle.
     *
     * @param width  New surface width in pixels.
     * @param height New surface height in pixels.
     */
    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) return
        hasSurface = true
        surfaceWidth = width
        surfaceHeight = height
        hasBootstrapFrame = false
        NativeBridge.nativeSetScreenSize(nativeHandle, width, height)
    }

    /**
     * Forwards a touch event to DasherCore.
     *
     * In [InputMode.TOUCH]:
     * - Action `0` (DOWN) resumes the engine and starts tracking.
     * - Action `1` (MOVE) forwards the new position while the finger is down.
     * - Action `2` (UP/CANCEL) sends a release and pauses the engine.
     *
     * In [InputMode.TILT] any touch action pauses the engine (tilt is the active input).
     *
     * @param action Android `MotionEvent` action (0 = DOWN, 1 = MOVE, 2 = UP/CANCEL).
     * @param x      Touch X coordinate in surface pixels.
     * @param y      Touch Y coordinate in surface pixels.
     */
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

    /**
     * Delivers a normalised tilt position to DasherCore.
     *
     * Both coordinates are in the range `[0, 1]` where `(0.5, 0.5)` is the neutral position.
     * This method is a no-op in [InputMode.TOUCH], when no surface is available, or when paused.
     *
     * @param normalizedX Normalised horizontal tilt component (0 = left, 1 = right).
     * @param normalizedY Normalised vertical tilt component (0 = top, 1 = bottom).
     */
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

    /**
     * Cancels any active tilt input by sending a synthetic UP event to DasherCore.
     *
     * Should be called when the tilt sensor is unregistered (e.g. on pause or mode change).
     */
    fun clearTiltInput() {
        if (destroyed || nativeHandle == 0L || !tiltActive) return
        NativeBridge.nativeTouch(nativeHandle, 2, currentX(), currentY())
        tiltActive = false
    }

    /**
     * Starts the [Choreographer] frame loop.
     *
     * Has no effect if the engine is already running or has been destroyed.
     */
    fun start() {
        if (running || destroyed) return
        running = true
        choreographer.postFrameCallback(this)
        emitStatus()
    }

    /**
     * Stops the [Choreographer] frame loop without destroying native resources.
     *
     * The engine can be restarted via [start].
     */
    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)
    }

    /**
     * Permanently shuts down the engine: stops the frame loop and destroys the native session.
     *
     * After this call the engine cannot be restarted.
     */
    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        if (nativeHandle != 0L) {
            NativeBridge.nativeDestroy(nativeHandle)
        }
    }

    /**
     * [Choreographer.FrameCallback] entry point – called once per vsync.
     *
     * Requests a new frame from DasherCore and dispatches the resulting draw-commands and
     * strings to [frameConsumer].  The last renderable frame is cached and re-emitted while
     * the engine is paused so that the UI does not go blank.
     *
     * @param frameTimeNanos Vsync timestamp in nanoseconds.
     */
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

    /**
     * Clears the native output-text buffer.
     *
     * Should be called when the host wants to start a new "word" or text session.
     */
    fun resetOutputText() {
        if (destroyed || nativeHandle == 0L) return
        NativeBridge.nativeResetOutputText(nativeHandle)
    }

    /**
     * Returns the [DasherLanguage] currently active in the native session.
     *
     * Falls back to [DasherLanguage.ENGLISH] when the engine is destroyed.
     */
    fun getLanguage(): DasherLanguage {
        if (destroyed || nativeHandle == 0L) return DasherLanguage.ENGLISH
        return DasherLanguage.fromAlphabetId(NativeBridge.nativeGetAlphabetId(nativeHandle))
    }

    /**
     * Attempts to switch the Dasher alphabet / language.
     *
     * The change is only applied while the engine is paused.
     *
     * @param language The desired [DasherLanguage].
     * @return `true` if the change was applied, `false` if the engine is running or destroyed.
     */
    fun setLanguage(language: DasherLanguage): Boolean {
        if (destroyed || nativeHandle == 0L) return false
        if (!isPaused) return false
        if (NativeBridge.nativeGetAlphabetId(nativeHandle) != language.alphabetId) {
            NativeBridge.nativeSetAlphabetId(nativeHandle, language.alphabetId)
        }
        hasBootstrapFrame = false
        return true
    }

    /**
     * Returns the [LanguageModel] currently active in the native session.
     *
     * Falls back to [LanguageModel.PPM] when the engine is destroyed.
     */
    fun getLanguageModel(): LanguageModel {
        if (destroyed || nativeHandle == 0L) return LanguageModel.PPM
        return LanguageModel.fromId(NativeBridge.nativeGetLanguageModelId(nativeHandle))
    }

    /**
     * Attempts to switch the language model.
     *
     * The change is only applied while the engine is paused.
     *
     * @param model The desired [LanguageModel].
     * @return `true` if the change was applied or the model is already set, `false` otherwise.
     */
    fun setLanguageModel(model: LanguageModel): Boolean {
        if (destroyed || nativeHandle == 0L) return false
        if (!isPaused) return false
        if (getLanguageModel() == model) return true
        NativeBridge.nativeSetLanguageModelId(nativeHandle, model.id)
        hasBootstrapFrame = false
        return true
    }

    /**
     * Returns the movement-speed multiplier currently in use (100 = normal speed).
     *
     * Falls back to `100` when the engine is destroyed.
     */
    fun getMovementSpeedPercent(): Int {
        if (destroyed || nativeHandle == 0L) return 100
        return NativeBridge.nativeGetMovementSpeedPercent(nativeHandle)
    }

    /**
     * Sets the movement-speed multiplier.
     *
     * @param percent Speed percentage; valid values are 50–400.
     * @return `true` if the value was forwarded to the native layer.
     */
    fun setMovementSpeedPercent(percent: Int): Boolean {
        if (destroyed || nativeHandle == 0L) return false
        NativeBridge.nativeSetMovementSpeedPercent(nativeHandle, percent)
        return true
    }

    /**
     * Replaces the active [frameConsumer] callback.
     *
     * Useful when the same engine is reused across configuration changes in
     * [DasherSessionCoordinator].
     *
     * @param consumer New callback invoked each frame with draw-commands and string labels.
     */
    fun setFrameConsumer(consumer: (IntArray, Array<String>) -> Unit) {
        frameConsumer = consumer
    }

    private fun currentX(): Float = surfaceWidth * 0.5f
    private fun currentY(): Float = surfaceHeight * 0.5f
}

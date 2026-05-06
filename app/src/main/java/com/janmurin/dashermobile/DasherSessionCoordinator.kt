package com.janmurin.dashermobile

import android.content.res.AssetManager

/**
 * Application-wide singleton that owns exactly one [DasherEngine] and multiplexes it across
 * multiple *host* clients (currently [MainActivity] and [DasherImeService]).
 *
 * ## Lifecycle model
 *
 * 1. A host calls [registerHost] to obtain a [HostHandle] and optionally create the engine.
 * 2. Before interacting with the engine the host calls [activateHost], which wires the engine's
 *    callbacks to that host and starts the frame loop.
 * 3. While active, the host delivers surface-size and input events via the coordinator.
 * 4. On pause the host calls [deactivateHost] to stop the frame loop and
 *    [discardHostRuntime] to tear down the native session entirely (important for the IME
 *    which must not consume resources when not visible).
 * 5. On final teardown the host calls [unregisterHost].
 *
 * All public methods are **thread-safe** (synchronized on an internal lock).
 * However, the [DasherEngine] itself must only be touched on the **main thread**; callers are
 * responsible for ensuring that constraint.
 */
object DasherSessionCoordinator {
    /**
     * Opaque token identifying a registered host client.
     *
     * Obtained from [registerHost] and required by every subsequent coordinator call.
     */
    data class HostHandle internal constructor(val id: Int)

    private data class HostCallbacks(
        val frameConsumer: (IntArray, Array<String>) -> Unit,
        val textConsumer: (String) -> Unit,
        val statusConsumer: (InputMode, Boolean) -> Unit
    )

    private data class HostSession(
        val filesDirPath: String,
        val assets: AssetManager,
        var callbacks: HostCallbacks,
        var surfaceWidth: Int = 0,
        var surfaceHeight: Int = 0
    )

    private val lock = Any()
    private var nextHostId = 1
    private var nativeHandle = 0L
    private var engine: DasherEngine? = null
    private var engineHostId: Int? = null
    private val hostSessions = mutableMapOf<Int, HostSession>()
    private var activeHostId: Int? = null

    /**
     * Registers a new host client and, if no native session exists yet, creates one.
     *
     * @param filesDirPath Absolute path to the host's private files directory.
     * @param assets       [AssetManager] from the host context; passed to the native engine so
     *                     DasherCore can read bundled alphabet XML files.
     * @return A [HostHandle] on success, or `null` if the native session could not be created.
     */
    fun registerHost(filesDirPath: String, assets: AssetManager): HostHandle? {
        synchronized(lock) {
            val handle = HostHandle(nextHostId++)
            hostSessions[handle.id] = HostSession(
                filesDirPath = filesDirPath,
                assets = assets,
                callbacks = HostCallbacks(
                    frameConsumer = { _, _ -> },
                    textConsumer = {},
                    statusConsumer = { _, _ -> }
                )
            )

            if (engine == null) {
                val localEngine = createEngine(filesDirPath, assets) ?: return null
                engine = localEngine
                engineHostId = handle.id
                activeHostId = handle.id
                bindCallbacks(localEngine, handle.id)
            }

            return handle
        }
    }

    /**
     * Replaces the callbacks used to deliver frames, text and status updates to [host].
     *
     * If [host] is currently the active host the new callbacks are wired into the engine
     * immediately.
     *
     * @param frameConsumer  Invoked each vsync with the flat draw-command array and string labels.
     * @param textConsumer   Invoked whenever the accumulated output text changes.
     * @param statusConsumer Invoked whenever the [InputMode] or pause state changes.
     */
    fun updateHostCallbacks(
        host: HostHandle,
        frameConsumer: (IntArray, Array<String>) -> Unit,
        textConsumer: (String) -> Unit,
        statusConsumer: (InputMode, Boolean) -> Unit
    ) {
        synchronized(lock) {
            val session = hostSessions[host.id] ?: return
            session.callbacks = HostCallbacks(frameConsumer, textConsumer, statusConsumer)
            val localEngine = engine ?: return
            if (activeHostId == host.id) {
                bindCallbacks(localEngine, host.id)
            }
        }
    }

    /**
     * Makes [host] the active host: wires its callbacks, restores surface size and starts
     * the Choreographer frame loop.
     *
     * If the engine was created for a different host it is destroyed and a fresh one is created
     * for [host].
     *
     * @param host The [HostHandle] that should become active.
     */
    fun activateHost(host: HostHandle) {
        synchronized(lock) {
            val session = hostSessions[host.id] ?: return

            val existingEngine = engine
            if (existingEngine != null && engineHostId == host.id) {
                activeHostId = host.id
                bindCallbacks(existingEngine, host.id)
                if (session.surfaceWidth > 0 && session.surfaceHeight > 0) {
                    existingEngine.onSurfaceSizeChanged(session.surfaceWidth, session.surfaceHeight)
                }
                existingEngine.start()
                return
            }

            engine?.let { existing ->
                existing.requestPause()
                existing.stop()
                existing.destroy()
            }

            val localEngine = createEngine(session.filesDirPath, session.assets) ?: run {
                engine = null
                nativeHandle = 0L
                engineHostId = null
                activeHostId = null
                return
            }

            engine = localEngine
            engineHostId = host.id
            activeHostId = host.id
            bindCallbacks(localEngine, host.id)
            if (session.surfaceWidth > 0 && session.surfaceHeight > 0) {
                localEngine.onSurfaceSizeChanged(session.surfaceWidth, session.surfaceHeight)
            }
            localEngine.start()
        }
    }

    /**
     * Pauses and stops the engine without destroying native resources, and clears the active
     * host reference.
     *
     * @param host The [HostHandle] to deactivate.  No-op if [host] is not currently active.
     */
    fun deactivateHost(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            val localEngine = engine ?: return
            localEngine.requestPause()
            localEngine.stop()
            activeHostId = null
        }
    }

    /**
     * Stops and destroys the native engine associated with [host].
     *
     * After this call the engine is gone but the host registration remains.  A subsequent call
     * to [activateHost] will recreate the engine.  This is the recommended pattern for the IME
     * which must release all resources when the keyboard is hidden.
     *
     * @param host The [HostHandle] whose runtime should be discarded.
     */
    fun discardHostRuntime(host: HostHandle) {
        synchronized(lock) {
            if (engineHostId != host.id) return
            val localEngine = engine
            localEngine?.requestPause()
            localEngine?.stop()
            localEngine?.destroy()
            engine = null
            nativeHandle = 0L
            engineHostId = null
            if (activeHostId == host.id) {
                activeHostId = null
            }
        }
    }

    /**
     * Fully removes [host] from the coordinator, stopping and destroying the engine if it was
     * owned by this host.
     *
     * @param host The [HostHandle] to unregister.
     */
    fun unregisterHost(host: HostHandle) {
        synchronized(lock) {
            val wasActive = activeHostId == host.id
            hostSessions.remove(host.id)

            val ownsEngine = engineHostId == host.id

            if (wasActive || ownsEngine) {
                val localEngine = engine
                localEngine?.requestPause()
                localEngine?.stop()
                localEngine?.destroy()
                activeHostId = null
                engine = null
                nativeHandle = 0L
                engineHostId = null
            }

            if (hostSessions.isEmpty()) {
                val localEngine = engine
                localEngine?.requestPause()
                localEngine?.stop()
                localEngine?.destroy()
                engine = null
                nativeHandle = 0L
                engineHostId = null
                activeHostId = null
            }
        }
    }

    /**
     * Executes [block] with the active [DasherEngine] while holding the internal lock.
     *
     * The block is skipped if [host] is not the currently active host or if no engine exists.
     *
     * @param host  The [HostHandle] that must be active.
     * @param block Lambda receiving the live [DasherEngine].
     */
    fun withEngine(host: HostHandle, block: (DasherEngine) -> Unit) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            val localEngine = engine ?: return
            block(localEngine)
        }
    }

    /** Returns the [InputMode] of the current engine, or [InputMode.TOUCH] if no engine exists. */
    fun getInputMode(): InputMode {
        synchronized(lock) {
            return engine?.getInputMode() ?: InputMode.TOUCH
        }
    }

    /** Returns `true` if the current engine is paused (or if no engine exists). */
    fun isPaused(): Boolean {
        synchronized(lock) {
            return engine?.isPaused() ?: true
        }
    }

    /** Returns the active [DasherLanguage], falling back to [DasherLanguage.ENGLISH]. */
    fun getLanguage(): DasherLanguage {
        synchronized(lock) {
            return engine?.getLanguage() ?: DasherLanguage.ENGLISH
        }
    }

    /** Returns the active [LanguageModel], falling back to [LanguageModel.PPM]. */
    fun getLanguageModel(): LanguageModel {
        synchronized(lock) {
            return engine?.getLanguageModel() ?: LanguageModel.PPM
        }
    }

    /** Returns the movement-speed percent, falling back to `100`. */
    fun getMovementSpeedPercent(): Int {
        synchronized(lock) {
            return engine?.getMovementSpeedPercent() ?: 100
        }
    }

    /**
     * Forwards a surface-size change event to the engine if [host] is active.
     *
     * The new dimensions are cached in the host session so they can be re-applied if the
     * engine is recreated later.
     */
    fun onSurfaceSizeChanged(host: HostHandle, width: Int, height: Int) {
        synchronized(lock) {
            val session = hostSessions[host.id] ?: return
            session.surfaceWidth = width
            session.surfaceHeight = height
            if (activeHostId != host.id) return
            engine?.onSurfaceSizeChanged(width, height)
        }
    }

    /**
     * Forwards a touch event to the engine if [host] is active.
     *
     * @param action 0 = DOWN, 1 = MOVE, 2 = UP/CANCEL.
     */
    fun onTouch(host: HostHandle, action: Int, x: Float, y: Float) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.onTouch(action, x, y)
        }
    }

    /**
     * Forwards a normalised tilt position to the engine if [host] is active.
     *
     * @param normalizedX Horizontal tilt in [0, 1].
     * @param normalizedY Vertical tilt in [0, 1].
     */
    fun onTiltNormalized(host: HostHandle, normalizedX: Float, normalizedY: Float) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.onTiltNormalized(normalizedX, normalizedY)
        }
    }

    /** Cancels active tilt input on the engine if [host] is active. */
    fun clearTiltInput(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.clearTiltInput()
        }
    }

    /** Requests the engine to pause if [host] is active. */
    fun requestPause(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.requestPause()
        }
    }

    /** Unpauses the engine if [host] is active. */
    fun unpause(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.unpause()
        }
    }

    /** Clears the output-text buffer on the engine if [host] is active. */
    fun resetOutputText(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.resetOutputText()
        }
    }

    /**
     * Attempts to switch the input mode on the engine if [host] is active.
     *
     * @return `true` if the switch succeeded.
     */
    fun setInputMode(host: HostHandle, mode: InputMode): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setInputMode(mode) == true
        }
    }

    /**
     * Attempts to switch the language on the engine if [host] is active.
     *
     * @return `true` if the language was changed.
     */
    fun setLanguage(host: HostHandle, language: DasherLanguage): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setLanguage(language) == true
        }
    }

    /**
     * Attempts to switch the language model on the engine if [host] is active.
     *
     * @return `true` if the model was changed.
     */
    fun setLanguageModel(host: HostHandle, model: LanguageModel): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setLanguageModel(model) == true
        }
    }

    /**
     * Sets the movement-speed percent on the engine if [host] is active.
     *
     * @return `true` if the value was applied.
     */
    fun setMovementSpeedPercent(host: HostHandle, percent: Int): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setMovementSpeedPercent(percent) == true
        }
    }

    private fun createEngine(filesDirPath: String, assets: AssetManager): DasherEngine? {
        val createdHandle = NativeBridge.nativeCreate(filesDirPath)
        if (createdHandle == 0L) {
            return null
        }
        NativeBridge.nativeSetAssetManager(createdHandle, assets)
        nativeHandle = createdHandle
        return DasherEngine(createdHandle) { _, _ -> }
    }

    private fun bindCallbacks(localEngine: DasherEngine, hostId: Int) {
        val callbacks = hostSessions[hostId]?.callbacks ?: return
        localEngine.setFrameConsumer(callbacks.frameConsumer)
        localEngine.onTextUpdate = callbacks.textConsumer
        localEngine.onStatusUpdate = callbacks.statusConsumer
        callbacks.statusConsumer(localEngine.getInputMode(), localEngine.isPaused())
        callbacks.textConsumer(NativeBridge.nativeGetOutputText(nativeHandle))
    }
}

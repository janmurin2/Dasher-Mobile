package com.janmurin.dashermobile

import android.content.res.AssetManager

object DasherSessionCoordinator {
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

    fun deactivateHost(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            val localEngine = engine ?: return
            localEngine.requestPause()
            localEngine.stop()
            activeHostId = null
        }
    }

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

    fun withEngine(host: HostHandle, block: (DasherEngine) -> Unit) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            val localEngine = engine ?: return
            block(localEngine)
        }
    }

    fun getInputMode(): InputMode {
        synchronized(lock) {
            return engine?.getInputMode() ?: InputMode.TOUCH
        }
    }

    fun isPaused(): Boolean {
        synchronized(lock) {
            return engine?.isPaused() ?: true
        }
    }

    fun getLanguage(): DasherLanguage {
        synchronized(lock) {
            return engine?.getLanguage() ?: DasherLanguage.ENGLISH
        }
    }

    fun getLanguageModel(): LanguageModel {
        synchronized(lock) {
            return engine?.getLanguageModel() ?: LanguageModel.PPM
        }
    }

    fun onSurfaceSizeChanged(host: HostHandle, width: Int, height: Int) {
        synchronized(lock) {
            val session = hostSessions[host.id] ?: return
            session.surfaceWidth = width
            session.surfaceHeight = height
            if (activeHostId != host.id) return
            engine?.onSurfaceSizeChanged(width, height)
        }
    }

    fun onTouch(host: HostHandle, action: Int, x: Float, y: Float) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.onTouch(action, x, y)
        }
    }

    fun onTiltNormalized(host: HostHandle, normalizedX: Float, normalizedY: Float) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.onTiltNormalized(normalizedX, normalizedY)
        }
    }

    fun clearTiltInput(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.clearTiltInput()
        }
    }

    fun requestPause(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.requestPause()
        }
    }

    fun unpause(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.unpause()
        }
    }

    fun resetOutputText(host: HostHandle) {
        synchronized(lock) {
            if (activeHostId != host.id) return
            engine?.resetOutputText()
        }
    }

    fun setInputMode(host: HostHandle, mode: InputMode): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setInputMode(mode) == true
        }
    }

    fun setLanguage(host: HostHandle, language: DasherLanguage): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setLanguage(language) == true
        }
    }

    fun setLanguageModel(host: HostHandle, model: LanguageModel): Boolean {
        synchronized(lock) {
            if (activeHostId != host.id) return false
            return engine?.setLanguageModel(model) == true
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

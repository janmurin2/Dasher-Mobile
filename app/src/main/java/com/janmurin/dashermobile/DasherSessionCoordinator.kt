package com.janmurin.dashermobile

import android.content.res.AssetManager

object DasherSessionCoordinator {
    data class HostHandle internal constructor(val id: Int)

    private data class HostCallbacks(
        val frameConsumer: (IntArray, Array<String>) -> Unit,
        val textConsumer: (String) -> Unit,
        val statusConsumer: (InputMode, Boolean) -> Unit
    )

    private val lock = Any()
    private var nextHostId = 1
    private var nativeHandle = 0L
    private var engine: DasherEngine? = null
    private val hostCallbacks = mutableMapOf<Int, HostCallbacks>()
    private var activeHostId: Int? = null

    fun registerHost(filesDirPath: String, assets: AssetManager): HostHandle? {
        synchronized(lock) {
            val localEngine = ensureEngine(filesDirPath, assets) ?: return null
            val handle = HostHandle(nextHostId++)
            hostCallbacks[handle.id] = HostCallbacks(
                frameConsumer = { _, _ -> },
                textConsumer = {},
                statusConsumer = { _, _ -> }
            )
            if (activeHostId == null) {
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
            if (!hostCallbacks.containsKey(host.id)) return
            hostCallbacks[host.id] = HostCallbacks(frameConsumer, textConsumer, statusConsumer)
            val localEngine = engine ?: return
            if (activeHostId == host.id) {
                bindCallbacks(localEngine, host.id)
            }
        }
    }

    fun activateHost(host: HostHandle) {
        synchronized(lock) {
            if (!hostCallbacks.containsKey(host.id)) return
            val localEngine = engine ?: return
            activeHostId = host.id
            bindCallbacks(localEngine, host.id)
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
            hostCallbacks.remove(host.id)

            val localEngine = engine
            if (wasActive && localEngine != null) {
                localEngine.requestPause()
                localEngine.stop()
                activeHostId = null
            }

            if (hostCallbacks.isEmpty()) {
                localEngine?.destroy()
                engine = null
                nativeHandle = 0L
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

    private fun ensureEngine(filesDirPath: String, assets: AssetManager): DasherEngine? {
        engine?.let { return it }
        val createdHandle = NativeBridge.nativeCreate(filesDirPath)
        if (createdHandle == 0L) {
            return null
        }
        NativeBridge.nativeSetAssetManager(createdHandle, assets)
        nativeHandle = createdHandle
        val createdEngine = DasherEngine(createdHandle) { _, _ -> }
        engine = createdEngine
        return createdEngine
    }

    private fun bindCallbacks(localEngine: DasherEngine, hostId: Int) {
        val callbacks = hostCallbacks[hostId] ?: return
        localEngine.setFrameConsumer(callbacks.frameConsumer)
        localEngine.onTextUpdate = callbacks.textConsumer
        localEngine.onStatusUpdate = callbacks.statusConsumer
        callbacks.statusConsumer(localEngine.getInputMode(), localEngine.isPaused())
        callbacks.textConsumer(NativeBridge.nativeGetOutputText(nativeHandle))
    }
}

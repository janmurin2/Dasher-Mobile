package com.janmurin.dashermobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.janmurin.dashermobile.ui.DasherCanvasView
import com.janmurin.dashermobile.ui.DasherHostUi

class MainActivity : ComponentActivity() {

    private var hostHandle: DasherSessionCoordinator.HostHandle? = null
    private lateinit var canvasView: DasherCanvasView
    private var tiltProvider: TiltInputProvider? = null
    private var isResumed = false
    private var suppressModeSwitchCallback = false

    private fun updateKeepScreenOn(mode: InputMode) {
        if (mode == InputMode.TILT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = NativeBridge.nativeVersion()
        Log.i("DasherMobile", "JNI loaded: $version")

        val restoredLanguage = DasherPrefs.getLanguage(this)
        var pendingStartupLanguage: DasherLanguage? = restoredLanguage

        hostHandle = DasherSessionCoordinator.registerHost(filesDir.absolutePath, assets)
        val hostViews = DasherHostUi.create(this, HostMode.APP)
        canvasView = hostViews.canvasView
        val textView = hostViews.outputView
        val statusView = hostViews.statusView
        val modeSwitch = requireNotNull(hostViews.modeSwitch)
        val calibrateButton = requireNotNull(hostViews.calibrateButton)

        setContentView(hostViews.root)
        hostViews.settingsButton?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        enableEdgeToEdge()

        val localHost = hostHandle
        if (localHost != null) {
            tiltProvider = TiltInputProvider(this) { nx, ny ->
                DasherSessionCoordinator.onTiltNormalized(localHost, nx, ny)
            }
            val tiltAvailable = tiltProvider?.hasSensor() == true
            if (!tiltAvailable) {
                modeSwitch.isEnabled = false
                calibrateButton.isEnabled = false
            }

            DasherSessionCoordinator.updateHostCallbacks(
                host = localHost,
                frameConsumer = { commands, strings -> canvasView.submitFrame(commands, strings) },
                textConsumer = { text -> textView.text = text },
                statusConsumer = { mode, paused ->
                    val modeLabel = if (mode == InputMode.TILT) "TILT" else "TOUCH"
                    val stateLabel = if (paused) "PAUSED" else "RUNNING"
                    statusView.text = "$modeLabel | $stateLabel"
                    updateKeepScreenOn(mode)
                    val switchChecked = mode == InputMode.TILT
                    if (modeSwitch.isChecked != switchChecked) {
                        suppressModeSwitchCallback = true
                        modeSwitch.isChecked = switchChecked
                        suppressModeSwitchCallback = false
                    }
                    calibrateButton.isEnabled = switchChecked
                    if (mode == InputMode.TILT && isResumed) {
                        if (!paused) tiltProvider?.register() else tiltProvider?.unregister()
                    }
                }
            )

            canvasView.onSurfaceSizeChanged = { width, height ->
                DasherSessionCoordinator.onSurfaceSizeChanged(localHost, width, height)
                val startupLanguage = pendingStartupLanguage
                if (startupLanguage != null && DasherSessionCoordinator.setLanguage(localHost, startupLanguage)) {
                    pendingStartupLanguage = null
                    DasherPrefs.setLanguage(this, startupLanguage)
                }
            }
            canvasView.onTouchInput = { action, x, y ->
                DasherSessionCoordinator.onTouch(localHost, action, x, y)
            }

            textView.setOnClickListener {
                DasherSessionCoordinator.resetOutputText(localHost)
            }

            modeSwitch.setOnCheckedChangeListener { _, checked ->
                if (suppressModeSwitchCallback) {
                    return@setOnCheckedChangeListener
                }
                if (!tiltAvailable && checked) {
                    suppressModeSwitchCallback = true
                    modeSwitch.isChecked = false
                    suppressModeSwitchCallback = false
                    return@setOnCheckedChangeListener
                }
                val mode = if (checked) InputMode.TILT else InputMode.TOUCH
                val switched = DasherSessionCoordinator.setInputMode(localHost, mode)
                if (!switched) {
                    val actualChecked = DasherSessionCoordinator.getInputMode() == InputMode.TILT
                    if (modeSwitch.isChecked != actualChecked) {
                        suppressModeSwitchCallback = true
                        modeSwitch.isChecked = actualChecked
                        suppressModeSwitchCallback = false
                    }
                    return@setOnCheckedChangeListener
                }
                calibrateButton.isEnabled = DasherSessionCoordinator.getInputMode() == InputMode.TILT
                if (!checked) {
                    tiltProvider?.unregister()
                    DasherSessionCoordinator.clearTiltInput(localHost)
                } else {
                    tiltProvider?.calibrate()
                }
            }

            calibrateButton.setOnClickListener {
                tiltProvider?.calibrate()
            }

            if (canvasView.width > 0 && canvasView.height > 0) {
                DasherSessionCoordinator.onSurfaceSizeChanged(localHost, canvasView.width, canvasView.height)
                val startupLanguage = pendingStartupLanguage
                if (startupLanguage != null && DasherSessionCoordinator.setLanguage(localHost, startupLanguage)) {
                    pendingStartupLanguage = null
                    DasherPrefs.setLanguage(this, startupLanguage)
                }
            }
        } else {
            Log.e("DasherMobile", "Failed to initialize native engine")
            modeSwitch.isEnabled = false
            calibrateButton.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        val localHost = hostHandle
        updateKeepScreenOn(DasherSessionCoordinator.getInputMode())
        if (localHost != null) {
            DasherSessionCoordinator.activateHost(localHost)

            // Apply preferences changed in Settings screen
            val prefLanguage = DasherPrefs.getLanguage(this)
            if (prefLanguage != DasherSessionCoordinator.getLanguage()) {
                DasherSessionCoordinator.setLanguage(localHost, prefLanguage)
            }
            val prefModel = DasherPrefs.getLanguageModel(this)
            if (prefModel != DasherSessionCoordinator.getLanguageModel()) {
                DasherSessionCoordinator.setLanguageModel(localHost, prefModel)
            }
            val prefMode = DasherPrefs.getInputMode(this)
            if (prefMode != DasherSessionCoordinator.getInputMode()) {
                DasherSessionCoordinator.setInputMode(localHost, prefMode)
            }
        }
        if (DasherSessionCoordinator.getInputMode() == InputMode.TILT && !DasherSessionCoordinator.isPaused()) {
            tiltProvider?.register()
        }
    }

    override fun onPause() {
        tiltProvider?.unregister()
        hostHandle?.let {
            DasherSessionCoordinator.clearTiltInput(it)
            DasherSessionCoordinator.deactivateHost(it)
        }
        isResumed = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        tiltProvider?.unregister()
        tiltProvider = null
        hostHandle?.let { DasherSessionCoordinator.unregisterHost(it) }
        hostHandle = null
        super.onDestroy()
    }
}

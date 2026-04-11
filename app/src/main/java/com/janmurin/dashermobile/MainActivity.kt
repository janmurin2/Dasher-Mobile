package com.janmurin.dashermobile

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    private var suppressLanguageSwitchCallback = false
    private var suppressLanguageModelCallback = false

    companion object {
        private const val PREFS_NAME = "dasher_mobile"
        private const val PREF_SELECTED_LANGUAGE = "selected_language"
    }

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

        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val restoredLanguage = DasherLanguage.fromPreferenceValue(
            preferences.getString(PREF_SELECTED_LANGUAGE, DasherLanguage.ENGLISH.preferenceValue)
        )
        var pendingStartupLanguage: DasherLanguage? = restoredLanguage

        hostHandle = DasherSessionCoordinator.registerHost(filesDir.absolutePath, assets)
        val hostViews = DasherHostUi.create(this, HostMode.APP)
        canvasView = hostViews.canvasView
        val textView = hostViews.outputView
        val statusView = hostViews.statusView
        val modeSwitch = requireNotNull(hostViews.modeSwitch)
        val calibrateButton = requireNotNull(hostViews.calibrateButton)
        val languageSpinner = hostViews.languageSpinner
        val languageModelSpinner = hostViews.languageModelSpinner
        val languages = listOf(DasherLanguage.ENGLISH, DasherLanguage.SLOVAK)
        val languageAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map {
                if (it == DasherLanguage.SLOVAK) getString(R.string.language_slovak) else getString(R.string.language_english)
            }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.adapter = languageAdapter

        val languageModels = listOf(LanguageModel.PPM, LanguageModel.WORD)
        val languageModelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageModels.map {
                if (it == LanguageModel.WORD) getString(R.string.language_model_word) else getString(R.string.language_model_ppm)
            }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageModelSpinner.adapter = languageModelAdapter

        setContentView(hostViews.root)
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
                    languageSpinner.isEnabled = paused
                    languageModelSpinner.isEnabled = paused
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
                    preferences.edit().putString(PREF_SELECTED_LANGUAGE, startupLanguage.preferenceValue).apply()
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

            val initialLanguageIndex = if (restoredLanguage == DasherLanguage.SLOVAK) 1 else 0
            suppressLanguageSwitchCallback = true
            languageSpinner.setSelection(initialLanguageIndex, false)
            suppressLanguageSwitchCallback = false

            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (suppressLanguageSwitchCallback) return
                    val selectedLanguage = languages[position]
                    val switched = DasherSessionCoordinator.setLanguage(localHost, selectedLanguage)
                    if (!switched) {
                        val actualLanguage = DasherSessionCoordinator.getLanguage()
                        val actualIndex = if (actualLanguage == DasherLanguage.SLOVAK) 1 else 0
                        suppressLanguageSwitchCallback = true
                        languageSpinner.setSelection(actualIndex, false)
                        suppressLanguageSwitchCallback = false
                        return
                    }
                    pendingStartupLanguage = null
                    preferences.edit().putString(PREF_SELECTED_LANGUAGE, selectedLanguage.preferenceValue).apply()
                    textView.text = ""
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            val currentLanguageModel = DasherSessionCoordinator.getLanguageModel()
            val initialIndex = if (currentLanguageModel == LanguageModel.WORD) 1 else 0
            suppressLanguageModelCallback = true
            languageModelSpinner.setSelection(initialIndex, false)
            suppressLanguageModelCallback = false

            languageModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (suppressLanguageModelCallback) return
                    val selectedModel = languageModels[position]
                    val switched = DasherSessionCoordinator.setLanguageModel(localHost, selectedModel)
                    if (!switched) {
                        val actualModel = DasherSessionCoordinator.getLanguageModel()
                        val actualIndex = if (actualModel == LanguageModel.WORD) 1 else 0
                        suppressLanguageModelCallback = true
                        languageModelSpinner.setSelection(actualIndex, false)
                        suppressLanguageModelCallback = false
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            calibrateButton.setOnClickListener {
                tiltProvider?.calibrate()
            }

            if (canvasView.width > 0 && canvasView.height > 0) {
                DasherSessionCoordinator.onSurfaceSizeChanged(localHost, canvasView.width, canvasView.height)
                val startupLanguage = pendingStartupLanguage
                if (startupLanguage != null && DasherSessionCoordinator.setLanguage(localHost, startupLanguage)) {
                    pendingStartupLanguage = null
                    preferences.edit().putString(PREF_SELECTED_LANGUAGE, startupLanguage.preferenceValue).apply()
                }
            }
        } else {
            Log.e("DasherMobile", "Failed to initialize native engine")
            modeSwitch.isEnabled = false
            calibrateButton.isEnabled = false
            languageSpinner.isEnabled = false
            languageModelSpinner.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        val localHost = hostHandle
        updateKeepScreenOn(DasherSessionCoordinator.getInputMode())
        if (localHost != null) {
            DasherSessionCoordinator.activateHost(localHost)
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

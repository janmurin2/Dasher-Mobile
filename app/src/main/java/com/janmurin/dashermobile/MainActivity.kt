package com.janmurin.dashermobile

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.janmurin.dashermobile.ui.DasherCanvasView

class MainActivity : ComponentActivity() {

    private var nativeHandle: Long = 0L
    private var engine: DasherEngine? = null
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

        nativeHandle = NativeBridge.nativeCreate(filesDir.absolutePath)
        canvasView = DasherCanvasView(this)

        val textView = TextView(this).apply {
            text = ""
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            val p = (8 * resources.displayMetrics.density).toInt()
            val pv = (6 * resources.displayMetrics.density).toInt()
            setPadding(p, pv, p, pv)
            setSingleLine(false)
            maxLines = 3
        }

        val textBoxHeight = (72 * resources.displayMetrics.density).toInt()

        val canvasFrame = FrameLayout(this)
        canvasFrame.addView(
            canvasView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val controlGap = (8 * resources.displayMetrics.density).toInt()
        fun controlLp() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = controlGap
        }

        // Row 1: Status, Mode Switch, Calibrate
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val statusView = TextView(this).apply {
            text = "TOUCH | PAUSED"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
            val p = (6 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            layoutParams = controlLp()
        }

        val modeSwitch = Switch(this).apply {
            text = getString(R.string.tilt_mode)
            isChecked = false
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = controlLp()
        }

        val calibrateButton = Button(this).apply {
            text = getString(R.string.calibrate)
            isEnabled = false
            layoutParams = controlLp()
        }

        row1.addView(statusView)
        row1.addView(modeSwitch)
        row1.addView(calibrateButton)

        // Row 2: Language + Spinner, Language Model + Spinner
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val languageLabel = TextView(this).apply {
            text = getString(R.string.language)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            layoutParams = controlLp()
        }

        val languages = listOf(DasherLanguage.ENGLISH, DasherLanguage.SLOVAK)
        val languageSpinner = Spinner(this).apply {
            minimumWidth = (100 * resources.displayMetrics.density).toInt()
            layoutParams = controlLp()
        }
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

        val languageModelLabel = TextView(this).apply {
            text = getString(R.string.language_model)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            layoutParams = controlLp()
        }
        val languageModels = listOf(LanguageModel.PPM, LanguageModel.WORD)
        val languageModelSpinner = Spinner(this).apply {
            minimumWidth = (88 * resources.displayMetrics.density).toInt()
            layoutParams = controlLp()
        }
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

        row2.addView(languageLabel)
        row2.addView(languageSpinner)
        row2.addView(languageModelLabel)
        row2.addView(languageModelSpinner)

        // Master controls panel: vertical layout at bottom with dark background
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            val p = (10 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        controls.addView(row1)
        controls.addView(row2)

        canvasFrame.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            textView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, textBoxHeight)
        )
        root.addView(
            canvasFrame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        enableEdgeToEdge()

        if (nativeHandle != 0L) {
            NativeBridge.nativeSetAssetManager(nativeHandle, assets)

            val localEngine = DasherEngine(nativeHandle) { commands, strings ->
                canvasView.submitFrame(commands, strings)
            }
            engine = localEngine

            tiltProvider = TiltInputProvider(this) { nx, ny ->
                localEngine.onTiltNormalized(nx, ny)
            }
            val tiltAvailable = tiltProvider?.hasSensor() == true
            if (!tiltAvailable) {
                modeSwitch.isEnabled = false
                calibrateButton.isEnabled = false
            }

            canvasView.onSurfaceSizeChanged = { width, height ->
                localEngine.onSurfaceSizeChanged(width, height)
                val startupLanguage = pendingStartupLanguage
                if (startupLanguage != null && localEngine.setLanguage(startupLanguage)) {
                    pendingStartupLanguage = null
                    preferences.edit().putString(PREF_SELECTED_LANGUAGE, startupLanguage.preferenceValue).apply()
                }
            }
            canvasView.onTouchInput = { action, x, y ->
                localEngine.onTouch(action, x, y)
            }

            localEngine.onTextUpdate = { text ->
                textView.text = text
            }

            localEngine.onStatusUpdate = { mode, paused ->
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

            textView.setOnClickListener {
                localEngine.resetOutputText()
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
                val switched = localEngine.setInputMode(mode)
                if (!switched) {
                    val actualChecked = localEngine.getInputMode() == InputMode.TILT
                    if (modeSwitch.isChecked != actualChecked) {
                        suppressModeSwitchCallback = true
                        modeSwitch.isChecked = actualChecked
                        suppressModeSwitchCallback = false
                    }
                    return@setOnCheckedChangeListener
                }
                calibrateButton.isEnabled = localEngine.getInputMode() == InputMode.TILT
                if (!checked) {
                    tiltProvider?.unregister()
                    localEngine.clearTiltInput()
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
                    val switched = localEngine.setLanguage(selectedLanguage)
                    if (!switched) {
                        val actualLanguage = localEngine.getLanguage()
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

            val currentLanguageModel = localEngine.getLanguageModel()
            val initialIndex = if (currentLanguageModel == LanguageModel.WORD) 1 else 0
            suppressLanguageModelCallback = true
            languageModelSpinner.setSelection(initialIndex, false)
            suppressLanguageModelCallback = false

            languageModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (suppressLanguageModelCallback) return
                    val selectedModel = languageModels[position]
                    val switched = localEngine.setLanguageModel(selectedModel)
                    if (!switched) {
                        val actualModel = localEngine.getLanguageModel()
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
                localEngine.onSurfaceSizeChanged(canvasView.width, canvasView.height)
                val startupLanguage = pendingStartupLanguage
                if (startupLanguage != null && localEngine.setLanguage(startupLanguage)) {
                    pendingStartupLanguage = null
                    preferences.edit().putString(PREF_SELECTED_LANGUAGE, startupLanguage.preferenceValue).apply()
                }
            }
        } else {
            Log.e("DasherMobile", "Failed to initialize native engine")
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        updateKeepScreenOn(engine?.getInputMode() ?: InputMode.TOUCH)
        engine?.start()
        if (engine?.getInputMode() == InputMode.TILT && engine?.isPaused() == false) {
            tiltProvider?.register()
        }
    }

    override fun onPause() {
        tiltProvider?.unregister()
        engine?.clearTiltInput()
        engine?.stop()
        isResumed = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        tiltProvider?.unregister()
        tiltProvider = null
        engine?.destroy()
        engine = null
        nativeHandle = 0L
        super.onDestroy()
    }
}

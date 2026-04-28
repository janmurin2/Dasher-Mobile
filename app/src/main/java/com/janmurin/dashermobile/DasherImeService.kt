package com.janmurin.dashermobile

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import com.janmurin.dashermobile.ui.DasherHostUi
import com.janmurin.dashermobile.ui.DasherHostViews

class DasherImeService : InputMethodService() {

    companion object {
        private const val TAG = "DasherImeService"
    }

    private var hostHandle: DasherSessionCoordinator.HostHandle? = null
    private var hostViews: DasherHostViews? = null
    private var imeTextSink: DasherImeTextSink? = null
    private var tiltProvider: TiltInputProvider? = null
    private var pendingStartupLanguage: DasherLanguage? = null
    private var suppressLanguageSwitchCallback = false
    private var suppressLanguageModelCallback = false
    private var suppressModeSwitchCallback = false
    private var shouldResetBufferOnNextStart = true
    private var isInputViewActive = false

    override fun onCreateInputView(): View {
        hostViews?.let { return it.root }

        val views = DasherHostUi.create(this, HostMode.IME)
        applyImeHeightSetting(views.root)
        hostViews = views
        imeTextSink = DasherImeTextSink(
            inputConnectionProvider = { currentInputConnection },
            editorInfoProvider = { currentInputEditorInfo }
        )
        pendingStartupLanguage = restoredLanguage()

        val localHost = DasherSessionCoordinator.registerHost(filesDir.absolutePath, assets)
        if (localHost == null) {
            Log.e(TAG, "Failed to initialize native engine for IME host")
            return views.root
        }
        hostHandle = localHost

        tiltProvider = TiltInputProvider(this) { nx, ny ->
            DasherSessionCoordinator.onTiltNormalized(localHost, nx, ny)
        }
        val tiltAvailable = tiltProvider?.hasSensor() == true
        if (!tiltAvailable) {
            views.modeSwitch?.isEnabled = false
        }

        DasherSessionCoordinator.updateHostCallbacks(
            host = localHost,
            frameConsumer = { commands, strings -> views.canvasView.submitFrame(commands, strings) },
            textConsumer = { text ->
                views.outputView.text = text
                imeTextSink?.onTextChanged(text)
            },
            statusConsumer = { mode, paused ->
                val modeLabel = if (mode == InputMode.TILT) "TILT" else "TOUCH"
                val stateLabel = if (paused) "PAUSED" else "RUNNING"
                views.statusView.text = "$modeLabel | $stateLabel"
                views.languageSpinner?.isEnabled = paused
                views.languageModelSpinner?.isEnabled = paused
                val showCanvasCalibrateButton = paused && mode == InputMode.TILT
                views.canvasCalibrateButton.visibility = if (showCanvasCalibrateButton) View.VISIBLE else View.GONE
                views.canvasCalibrateButton.isEnabled = showCanvasCalibrateButton
                views.canvasView.showPauseOverlay = paused
                
                val switchChecked = mode == InputMode.TILT
                if (views.modeSwitch?.isChecked != switchChecked) {
                    suppressModeSwitchCallback = true
                    views.modeSwitch?.isChecked = switchChecked
                    suppressModeSwitchCallback = false
                }
                views.calibrateButton?.isEnabled = switchChecked
                
                if (mode == InputMode.TILT && isInputViewActive) {
                    if (!paused) {
                        tiltProvider?.register()
                    } else {
                        tiltProvider?.unregister()
                    }
                } else {
                    tiltProvider?.unregister()
                }
            }
        )

        views.canvasView.onSurfaceSizeChanged = { width, height ->
            DasherSessionCoordinator.onSurfaceSizeChanged(localHost, width, height)
            applyPendingLanguage(localHost)
        }
        views.canvasView.onTouchInput = { action, x, y ->
            DasherSessionCoordinator.onTouch(localHost, action, x, y)
        }

        views.modeSwitch?.setOnCheckedChangeListener { _, checked ->
            if (suppressModeSwitchCallback) return@setOnCheckedChangeListener
            val mode = if (checked) InputMode.TILT else InputMode.TOUCH
            val switched = DasherSessionCoordinator.setInputMode(localHost, mode)
            if (switched) {
                DasherPrefs.setInputMode(this, mode)
            } else {
                val actualChecked = DasherSessionCoordinator.getInputMode() == InputMode.TILT
                if (views.modeSwitch?.isChecked != actualChecked) {
                    suppressModeSwitchCallback = true
                    views.modeSwitch?.isChecked = actualChecked
                    suppressModeSwitchCallback = false
                }
            }
        }

        views.calibrateButton?.setOnClickListener {
            tiltProvider?.calibrate()
            DasherSessionCoordinator.unpause(localHost)
        }

        views.canvasCalibrateButton.setOnClickListener {
            tiltProvider?.calibrate()
            DasherSessionCoordinator.unpause(localHost)
        }

        setupLanguageControls(views, localHost)
        setupLanguageModelControls(views, localHost)
        applyImeStartupState(localHost)

        if (views.canvasView.width > 0 && views.canvasView.height > 0) {
            DasherSessionCoordinator.onSurfaceSizeChanged(localHost, views.canvasView.width, views.canvasView.height)
            applyPendingLanguage(localHost)
        }

        return views.root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        pendingStartupLanguage = restoredLanguage()
        shouldResetBufferOnNextStart = true
        hostHandle?.let { applyImeStartupState(it) }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val localHost = ensureHost() ?: return
        hostViews?.let { applyImeHeightSetting(it.root) }
        isInputViewActive = true
        DasherSessionCoordinator.activateHost(localHost)
        applyImeStartupState(localHost)
        
        DasherSessionCoordinator.requestPause(localHost)
        imeTextSink?.resetTracking()
        hostViews?.outputView?.text = ""
        DasherSessionCoordinator.resetOutputText(localHost)
        
        if (DasherSessionCoordinator.getInputMode() == InputMode.TILT && !DasherSessionCoordinator.isPaused()) {
            tiltProvider?.register()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        isInputViewActive = false
        tiltProvider?.unregister()
        hostHandle?.let {
            DasherSessionCoordinator.clearTiltInput(it)
            DasherSessionCoordinator.requestPause(it)
            DasherSessionCoordinator.deactivateHost(it)
            DasherSessionCoordinator.discardHostRuntime(it)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        tiltProvider?.unregister()
        tiltProvider = null
        hostHandle?.let { DasherSessionCoordinator.unregisterHost(it) }
        hostHandle = null
        hostViews = null
        imeTextSink = null
        super.onDestroy()
    }

    private fun ensureHost(): DasherSessionCoordinator.HostHandle? {
        val existing = hostHandle
        if (existing != null) {
            return existing
        }
        onCreateInputView()
        return hostHandle
    }

    private fun applyImeStartupState(localHost: DasherSessionCoordinator.HostHandle) {
        DasherSessionCoordinator.requestPause(localHost)
        
        val prefMode = DasherPrefs.getInputMode(this)
        if (prefMode != DasherSessionCoordinator.getInputMode()) {
            DasherSessionCoordinator.setInputMode(localHost, prefMode)
        }
        
        val prefModel = DasherPrefs.getLanguageModel(this)
        if (prefModel != DasherSessionCoordinator.getLanguageModel()) {
            DasherSessionCoordinator.setLanguageModel(localHost, prefModel)
        }
        
        syncLanguageSelection()
        syncLanguageModelSelection()


        applyPendingLanguage(localHost)
    }

    private fun applyPendingLanguage(localHost: DasherSessionCoordinator.HostHandle) {
        val startupLanguage = pendingStartupLanguage ?: return
        if (DasherSessionCoordinator.setLanguage(localHost, startupLanguage)) {
            pendingStartupLanguage = null
            DasherPrefs.setLanguage(this, startupLanguage)
            syncLanguageSelection()
        }
    }

    private fun setupLanguageControls(views: DasherHostViews, localHost: DasherSessionCoordinator.HostHandle) {
        val languages = listOf(DasherLanguage.ENGLISH, DasherLanguage.SLOVAK)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map {
                if (it == DasherLanguage.SLOVAK) getString(R.string.language_slovak) else getString(R.string.language_english)
            }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        views.languageSpinner?.adapter = adapter
        syncLanguageSelection()

        views.languageSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressLanguageSwitchCallback) return
                val selectedLanguage = languages[position]
                imeTextSink?.resetTracking()
                val switched = DasherSessionCoordinator.setLanguage(localHost, selectedLanguage)
                if (!switched) {
                    syncLanguageSelection()
                    return
                }
                pendingStartupLanguage = null
                DasherPrefs.setLanguage(this@DasherImeService, selectedLanguage)
                views.outputView.text = ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupLanguageModelControls(views: DasherHostViews, localHost: DasherSessionCoordinator.HostHandle) {
        val models = listOf(LanguageModel.PPM, LanguageModel.WORD, LanguageModel.KENLM)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models.map {
                when (it) {
                    LanguageModel.PPM -> getString(R.string.language_model_ppm)
                    LanguageModel.WORD -> getString(R.string.language_model_word)
                    LanguageModel.KENLM -> getString(R.string.language_model_kenlm)
                }
            }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        views.languageModelSpinner?.adapter = adapter
        syncLanguageModelSelection()

        views.languageModelSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressLanguageModelCallback) return
                val selectedModel = models[position]
                if (!DasherSessionCoordinator.setLanguageModel(localHost, selectedModel)) {
                    syncLanguageModelSelection()
                    return
                }
                DasherPrefs.setLanguageModel(this@DasherImeService, selectedModel)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun syncLanguageSelection() {
        val views = hostViews ?: return
        val selection = if (DasherSessionCoordinator.getLanguage() == DasherLanguage.SLOVAK) 1 else 0
        suppressLanguageSwitchCallback = true
        views.languageSpinner?.setSelection(selection, false)
        suppressLanguageSwitchCallback = false
    }

    private fun syncLanguageModelSelection() {
        val views = hostViews ?: return
        val models = listOf(LanguageModel.PPM, LanguageModel.WORD, LanguageModel.KENLM)
        val selection = models.indexOf(DasherSessionCoordinator.getLanguageModel()).coerceAtLeast(0)
        suppressLanguageModelCallback = true
        views.languageModelSpinner?.setSelection(selection, false)
        suppressLanguageModelCallback = false
    }

    private fun restoredLanguage(): DasherLanguage {
        return DasherPrefs.getLanguage(this)
    }

    private fun applyImeHeightSetting(rootView: View) {
        val percent = DasherPrefs.getImeHeightPercent(this)
        val screenHeight = resources.displayMetrics.heightPixels
        if (screenHeight <= 0) return

        val targetHeight = (screenHeight * (percent / 100f)).toInt()
        val layoutParams = rootView.layoutParams
        if (layoutParams != null) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = targetHeight
            rootView.layoutParams = layoutParams
        } else {
            rootView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeight
            )
        }
        rootView.minimumHeight = targetHeight
        rootView.requestLayout()
    }
}

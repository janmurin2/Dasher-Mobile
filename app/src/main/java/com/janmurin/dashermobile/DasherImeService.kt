package com.janmurin.dashermobile

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.janmurin.dashermobile.ui.DasherHostUi
import com.janmurin.dashermobile.ui.DasherHostViews

class DasherImeService : InputMethodService() {

    companion object {
        private const val TAG = "DasherImeService"
        private const val PREFS_NAME = "dasher_mobile"
        private const val PREF_SELECTED_LANGUAGE = "selected_language"
    }

    private var hostHandle: DasherSessionCoordinator.HostHandle? = null
    private var hostViews: DasherHostViews? = null
    private var imeTextSink: DasherImeTextSink? = null
    private var pendingStartupLanguage: DasherLanguage? = null
    private var suppressLanguageSwitchCallback = false
    private var suppressLanguageModelCallback = false
    private var shouldResetBufferOnNextStart = true

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateInputView(): View {
        hostViews?.let { return it.root }

        val views = DasherHostUi.create(this, HostMode.IME)
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
                views.languageSpinner.isEnabled = paused
                views.languageModelSpinner.isEnabled = paused
            }
        )

        views.canvasView.onSurfaceSizeChanged = { width, height ->
            DasherSessionCoordinator.onSurfaceSizeChanged(localHost, width, height)
            applyPendingLanguage(localHost)
        }
        views.canvasView.onTouchInput = { action, x, y ->
            DasherSessionCoordinator.onTouch(localHost, action, x, y)
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
        DasherSessionCoordinator.activateHost(localHost)
        applyImeStartupState(localHost)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hostHandle?.let {
            DasherSessionCoordinator.requestPause(it)
            DasherSessionCoordinator.deactivateHost(it)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
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
        DasherSessionCoordinator.setInputMode(localHost, InputMode.TOUCH)
        syncLanguageSelection()
        syncLanguageModelSelection()

        if (shouldResetBufferOnNextStart) {
            imeTextSink?.resetTracking()
            hostViews?.outputView?.text = ""
            DasherSessionCoordinator.resetOutputText(localHost)
            shouldResetBufferOnNextStart = false
        }

        applyPendingLanguage(localHost)
    }

    private fun applyPendingLanguage(localHost: DasherSessionCoordinator.HostHandle) {
        val startupLanguage = pendingStartupLanguage ?: return
        if (DasherSessionCoordinator.setLanguage(localHost, startupLanguage)) {
            pendingStartupLanguage = null
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
        views.languageSpinner.adapter = adapter
        syncLanguageSelection()

        views.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
                preferences.edit().putString(PREF_SELECTED_LANGUAGE, selectedLanguage.preferenceValue).apply()
                views.outputView.text = ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupLanguageModelControls(views: DasherHostViews, localHost: DasherSessionCoordinator.HostHandle) {
        val models = listOf(LanguageModel.PPM, LanguageModel.WORD)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models.map {
                if (it == LanguageModel.WORD) getString(R.string.language_model_word) else getString(R.string.language_model_ppm)
            }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        views.languageModelSpinner.adapter = adapter
        syncLanguageModelSelection()

        views.languageModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressLanguageModelCallback) return
                val selectedModel = models[position]
                if (!DasherSessionCoordinator.setLanguageModel(localHost, selectedModel)) {
                    syncLanguageModelSelection()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun syncLanguageSelection() {
        val views = hostViews ?: return
        val selection = if (DasherSessionCoordinator.getLanguage() == DasherLanguage.SLOVAK) 1 else 0
        suppressLanguageSwitchCallback = true
        views.languageSpinner.setSelection(selection, false)
        suppressLanguageSwitchCallback = false
    }

    private fun syncLanguageModelSelection() {
        val views = hostViews ?: return
        val selection = if (DasherSessionCoordinator.getLanguageModel() == LanguageModel.WORD) 1 else 0
        suppressLanguageModelCallback = true
        views.languageModelSpinner.setSelection(selection, false)
        suppressLanguageModelCallback = false
    }

    private fun restoredLanguage(): DasherLanguage {
        return DasherLanguage.fromPreferenceValue(
            preferences.getString(PREF_SELECTED_LANGUAGE, DasherLanguage.ENGLISH.preferenceValue)
        )
    }
}


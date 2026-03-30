package com.janmurin.dashermobile

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        val modeSwitch = Switch(this).apply {
            text = getString(R.string.tilt_mode)
            isChecked = false
        }
        val calibrateButton = Button(this).apply {
            text = getString(R.string.calibrate)
            isEnabled = false
        }
        val statusView = TextView(this).apply {
            text = "TOUCH | PAUSED"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xAA000000.toInt())
            val p = (6 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        controls.addView(statusView)
        controls.addView(modeSwitch)
        controls.addView(calibrateButton)
        canvasFrame.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
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

            calibrateButton.setOnClickListener {
                tiltProvider?.calibrate()
            }

            if (canvasView.width > 0 && canvasView.height > 0) {
                localEngine.onSurfaceSizeChanged(canvasView.width, canvasView.height)
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

package com.janmurin.dashermobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.janmurin.dashermobile.ui.DasherCanvasView

class MainActivity : ComponentActivity() {

    private var nativeHandle: Long = 0L
    private var engine: DasherEngine? = null
    private lateinit var canvasView: DasherCanvasView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = NativeBridge.nativeVersion()
        Log.i("DasherMobile", "JNI loaded: $version")

        nativeHandle = NativeBridge.nativeCreate(filesDir.absolutePath)

        canvasView = DasherCanvasView(this)
        setContentView(canvasView)
        enableEdgeToEdge()

        if (nativeHandle != 0L) {
            NativeBridge.nativeSetAssetManager(nativeHandle, assets)

            val localEngine = DasherEngine(nativeHandle) { commands ->
                canvasView.submitFrame(commands)
            }
            engine = localEngine

            canvasView.onSurfaceSizeChanged = { width, height ->
                localEngine.onSurfaceSizeChanged(width, height)
            }
            canvasView.onTouchInput = { action, x, y ->
                localEngine.onTouch(action, x, y)
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
        engine?.start()
    }

    override fun onPause() {
        engine?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        engine?.destroy()
        engine = null
        nativeHandle = 0L
        super.onDestroy()
    }
}

package com.janmurin.dashermobile.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.janmurin.dashermobile.HostMode
import com.janmurin.dashermobile.R

data class DasherHostViews(
    val root: View,
    val canvasView: DasherCanvasView,
    val outputView: TextView,
    val statusView: TextView,
    val modeSwitch: Switch?,
    val calibrateButton: Button?,
    val languageSpinner: Spinner?,
    val languageModelSpinner: Spinner?,
    val settingsButton: View?
)

object DasherHostUi {
    fun create(context: Context, hostMode: HostMode): DasherHostViews {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val canvasView = DasherCanvasView(context)
        val outputView = TextView(context).apply {
            text = ""
            textSize = if (hostMode.isCompact) 15f else 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setSingleLine(false)
            maxLines = if (hostMode.isCompact) 2 else 3
        }

        val statusView = TextView(context).apply {
            text = "TOUCH | PAUSED"
            textSize = if (hostMode.isCompact) 11f else 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1B5E20.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        fun controlLp(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dp(8)
        }

        fun label(textRes: Int): TextView = TextView(context).apply {
            text = context.getString(textRes)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = if (hostMode.isCompact) 11f else 12f
            layoutParams = controlLp()
        }

        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        statusView.layoutParams = controlLp()
        row1.addView(statusView)

        val modeSwitch = if (hostMode.allowsTiltControls) {
            Switch(context).apply {
                text = context.getString(R.string.tilt_mode)
                isChecked = false
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = controlLp()
            }.also(row1::addView)
        } else {
            null
        }

        val calibrateButton = if (hostMode.allowsTiltControls) {
            Button(context).apply {
                text = context.getString(R.string.calibrate)
                isEnabled = false
                layoutParams = controlLp()
            }.also(row1::addView)
        } else {
            null
        }

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(row1)
        }

        var languageSpinner: Spinner? = null
        var languageModelSpinner: Spinner? = null

        if (hostMode.isCompact) {
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            languageSpinner = Spinner(context).apply {
                minimumWidth = dp(if (hostMode.isCompact) 84 else 100)
                layoutParams = controlLp()
            }
            languageModelSpinner = Spinner(context).apply {
                minimumWidth = dp(if (hostMode.isCompact) 72 else 88)
                layoutParams = controlLp()
            }

            row2.addView(label(R.string.language))
            row2.addView(languageSpinner)
            row2.addView(label(R.string.language_model))
            row2.addView(languageModelSpinner)
            controls.addView(row2)
        }

        val canvasFrame = FrameLayout(context).apply {
            addView(
                canvasView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                controls,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(top = insets.top, bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }

        var settingsIcon: ImageView? = null
        if (hostMode == HostMode.APP) {
            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFF1C1B1F.toInt())
                setPadding(dp(16), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = TextView(context).apply {
                text = context.getString(R.string.app_name)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            settingsIcon = ImageView(context).apply {
                setImageResource(R.drawable.settings_24px)
                contentDescription = context.getString(R.string.icon_settings)
                val size = dp(48)
                val iconPadding = dp(12)
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                layoutParams = LinearLayout.LayoutParams(size, size)
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            topBar.addView(titleView)
            topBar.addView(settingsIcon)
            root.addView(topBar)
        }

        root.addView(
            outputView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(if (hostMode.isCompact) 48 else 72)
            )
        )
        root.addView(
            canvasFrame,
            if (hostMode.isCompact) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(240)
                )
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
        )

        return DasherHostViews(
            root = root,
            canvasView = canvasView,
            outputView = outputView,
            statusView = statusView,
            modeSwitch = modeSwitch,
            calibrateButton = calibrateButton,
            languageSpinner = languageSpinner,
            languageModelSpinner = languageModelSpinner,
            settingsButton = settingsIcon
        )
    }
}

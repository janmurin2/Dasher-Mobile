package com.janmurin.dashermobile.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
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
    val modeSwitch: SwitchCompat?,
    val calibrateButton: Button?,
    val languageSpinner: Spinner?,
    val languageModelSpinner: Spinner?,
    val settingsButton: View?,
    val canvasFrame: FrameLayout,
    val canvasCalibrateButton: Button
)

object DasherHostUi {
    fun create(context: Context, hostMode: HostMode): DasherHostViews {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val canvasView = DasherCanvasView(context)
        val outputView = TextView(context).apply {
            text = ""
            textSize = if (hostMode.isCompact) 15f else 18f
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setSingleLine(false)
            maxLines = if (hostMode.isCompact) 2 else 3
            movementMethod = ScrollingMovementMethod()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val layout = layout
                    if (layout != null) {
                        val scrollAmount = layout.height - height + totalPaddingTop + totalPaddingBottom
                        if (scrollAmount > 0) {
                            scrollTo(0, scrollAmount)
                        } else {
                            scrollTo(0, 0)
                        }
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        val statusView = TextView(context).apply {
            text = context.getString(R.string.status_initial)
            textSize = if (hostMode.isCompact) 11f else 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF000000.toInt())
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
            SwitchCompat(context).apply {
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
                isAllCaps = false
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
            visibility = View.GONE
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
                minimumWidth = dp(84)
                layoutParams = controlLp()
            }
            languageModelSpinner = Spinner(context).apply {
                minimumWidth = dp(72)
                layoutParams = controlLp()
            }

            row2.addView(label(R.string.language))
            row2.addView(languageSpinner)
            row2.addView(label(R.string.language_model))
            row2.addView(languageModelSpinner)
            controls.addView(row2)
        }

        val canvasFrame = FrameLayout(context)
        canvasFrame.addView(
            canvasView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        canvasFrame.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        val canvasCalibrateButton = Button(context).apply {
            text = context.getString(R.string.calibrate).lowercase()
            isAllCaps = false
            textSize = 16f
            minHeight = dp(50)
            minWidth = dp(124)
            setTextColor(0xFFFFFFFF.toInt())
            background = ResourcesCompat.getDrawable(context.resources, R.drawable.button_background, null)
            typeface = ResourcesCompat.getFont(context, R.font.inter_italic)
            val horizontalPadding = dp(18)
            val verticalPadding = dp(12)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                val margin = dp(16)
                bottomMargin = margin
                leftMargin = margin
            }
            visibility = View.GONE
        }
        canvasFrame.addView(canvasCalibrateButton)

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
                setBackgroundColor(0xFF000000.toInt())
                setPadding(dp(16), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = TextView(context).apply {
                text = context.getString(R.string.app_name).lowercase()
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 22f
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold_italic)
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            settingsIcon = ImageView(context).apply {
                setImageResource(R.drawable.settings_24px)
                setColorFilter(0xFFFFFFFF.toInt())
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

        if (hostMode == HostMode.APP) {
            val outputFrame = FrameLayout(context)
            outputFrame.addView(
                outputView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val copyIcon = ImageView(context).apply {
                setImageResource(R.drawable.content_copy_24px)
                setColorFilter(0xFF000000.toInt())
                val size = dp(24)
                val margin = dp(8)
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = margin
                    bottomMargin = margin
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Copied Text", outputView.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            outputFrame.addView(copyIcon)

            root.addView(
                outputFrame,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72)
                )
            )
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(divider)

        root.addView(
            canvasFrame,
            if (hostMode.isCompact) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
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
            settingsButton = settingsIcon,
            canvasFrame = canvasFrame,
            canvasCalibrateButton = canvasCalibrateButton
        )
    }
}

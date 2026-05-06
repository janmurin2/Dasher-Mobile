package com.janmurin.dashermobile.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import android.annotation.SuppressLint

/**
 * Custom [View] that renders a Dasher frame from the flat integer draw-command buffer
 * produced by [com.janmurin.dashermobile.NativeBridge.nativeFrame].
 *
 * ## Draw-command format
 * Each frame is a flat [IntArray] of 6-integer records `[op, a, b, c, d, argb]`:
 * | op | Primitive | Parameters |
 * |----|-----------|-----------|
 * | 0  | Clear canvas | – |
 * | 1  | Circle | cx=a, cy=b, r=c, filled=(d≠0) |
 * | 2  | Line | x1=a, y1=b, x2=c, y2=d |
 * | 3  | Stroked rect | x1=a, y1=b, x2=c, y2=d |
 * | 4  | Filled rect  | x1=a, y1=b, x2=c, y2=d |
 * | 5  | Text | x=a, y=b, fontSize=c, stringIndex=d |
 *
 * ## Coordinate normalisation
 * If the coordinate bounds of the incoming commands fall entirely outside the view, or span
 * an area more than 4× larger than the view, the commands are linearly scaled and translated
 * to fill the view.  This ensures Dasher content is always visible even when screen-space
 * coordinates diverge from view pixels (e.g. during engine warm-up).
 *
 * ## Pause overlay
 * When [showPauseOverlay] is `true` a semi-transparent grey overlay is drawn on top of the
 * last rendered frame to indicate that Dasher is paused.
 *
 * @see com.janmurin.dashermobile.DasherEngine
 */
class DasherCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
    }

    private var commands: IntArray = IntArray(0)
    private var strings: Array<String> = emptyArray()
    private var drawCount = 0

    /**
     * When `true` a semi-transparent grey overlay is drawn over the canvas to signal
     * that the Dasher engine is paused.
     */
    var showPauseOverlay: Boolean = false

    /**
     * Called when the view's pixel dimensions change (first layout, orientation change, etc.).
     * Receives the new width and height in pixels.  Wire this to
     * [com.janmurin.dashermobile.DasherSessionCoordinator.onSurfaceSizeChanged].
     */
    var onSurfaceSizeChanged: ((Int, Int) -> Unit)? = null

    /**
     * Called on every touch event with `(action, x, y)` where action follows Android's
     * `MotionEvent` convention: 0 = DOWN, 1 = MOVE, 2 = UP/CANCEL.
     */
    var onTouchInput: ((Int, Float, Float) -> Unit)? = null

    /**
     * Accepts a new frame to render and schedules a redraw on the next animation frame.
     *
     * @param frameCommands Flat draw-command array from [com.janmurin.dashermobile.NativeBridge.nativeFrame].
     * @param frameStrings  String labels from [com.janmurin.dashermobile.NativeBridge.nativeGetFrameStrings].
     */
    fun submitFrame(frameCommands: IntArray, frameStrings: Array<String> = emptyArray()) {
        commands = frameCommands
        strings = frameStrings
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSurfaceSizeChanged?.invoke(w, h)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchInput?.invoke(0, event.x, event.y)
            MotionEvent.ACTION_MOVE -> onTouchInput?.invoke(1, event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchInput?.invoke(2, event.x, event.y)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = commands
        if (data.isEmpty()) return

        if (showPauseOverlay) {
            fillPaint.color = 0x80D3D3D3.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        }

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        var k = 0
        while (k + 5 < data.size) {
            val op = data[k]
            val a = data[k + 1]
            val b = data[k + 2]
            val c = data[k + 3]
            when (op) {
                1 -> {
                    minX = min(minX, a - c)
                    maxX = max(maxX, a + c)
                    minY = min(minY, b - c)
                    maxY = max(maxY, b + c)
                }
                2, 3, 4 -> {
                    minX = min(minX, min(a, c))
                    maxX = max(maxX, max(a, c))
                    minY = min(minY, min(b, data[k + 4]))
                    maxY = max(maxY, max(b, data[k + 4]))
                }
            }
            k += 6
        }

        val hasBounds = minX <= maxX && minY <= maxY
        val spanX = if (hasBounds) max(1, maxX - minX) else 1
        val spanY = if (hasBounds) max(1, maxY - minY) else 1
        val offScreen = hasBounds && (maxX < 0 || minX > width || maxY < 0 || minY > height)
        val hugeSpan = hasBounds && (spanX > width * 4 || spanY > height * 4)
        val normalize = hasBounds && width > 0 && height > 0 && (offScreen || hugeSpan)

        val sx = if (normalize) width.toFloat() / spanX.toFloat() else 1f
        val sy = if (normalize) height.toFloat() / spanY.toFloat() else 1f
        val tx = if (normalize) (-minX).toFloat() else 0f
        val ty = if (normalize) (-minY).toFloat() else 0f

        fun mapX(value: Int): Float = if (normalize) (value.toFloat() + tx) * sx else value.toFloat()
        fun mapY(value: Int): Float = if (normalize) (value.toFloat() + ty) * sy else value.toFloat()
        fun mapR(value: Int): Float {
            if (!normalize) return value.toFloat()
            return value * (sx + sy) * 0.5f
        }

        val localStrings = strings
        var textOpCount = 0

        var i = 0
        while (i + 5 < data.size) {
            val op = data[i]
            val a = data[i + 1]
            val b = data[i + 2]
            val c = data[i + 3]
            val d = data[i + 4]
            val color = data[i + 5]
            when (op) {
                0 -> canvas.drawColor(color)
                1 -> {
                    val paint = if (d != 0) fillPaint else strokePaint
                    paint.color = color
                    canvas.drawCircle(mapX(a), mapY(b), mapR(c), paint)
                }
                2 -> {
                    strokePaint.color = color
                    strokePaint.strokeWidth = 4f
                    canvas.drawLine(mapX(a), mapY(b), mapX(c), mapY(d), strokePaint)
                }
                3 -> {
                    strokePaint.color = color
                    val l = min(mapX(a), mapX(c))
                    val r = max(mapX(a), mapX(c))
                    val t = min(mapY(b), mapY(d))
                    val btm = max(mapY(b), mapY(d))
                    canvas.drawRect(l, t, r, btm, strokePaint)
                }
                4 -> {
                    fillPaint.color = color
                    val l = min(mapX(a), mapX(c))
                    val r = max(mapX(a), mapX(c))
                    val t = min(mapY(b), mapY(d))
                    val btm = max(mapY(b), mapY(d))
                    canvas.drawRect(l, t, r, btm, fillPaint)
                }
                5 -> {
                    val strIdx = d
                    if (strIdx in localStrings.indices) {
                        val fs = (mapR(c) * 2.5f).coerceAtLeast(8f)
                        textPaint.textSize = fs
                        textPaint.color = color
                        val fm = textPaint.fontMetrics
                        canvas.drawText(localStrings[strIdx], mapX(a), mapY(b) - fm.top, textPaint)
                        textOpCount++
                    } else {
                        Log.w("DasherCanvasView", "String index out of bounds: $strIdx")
                    }
                }
                else -> Unit
            }
            i += 6
        }

        drawCount += 1
        if (drawCount % 120 == 0) {
            val firstOp = data[0]
            Log.d("DasherRender", "cmds=${data.size / 6} textOps=$textOpCount strings=${localStrings.size} firstOp=$firstOp bounds=[$minX,$minY]-[$maxX,$maxY] normalize=$normalize")
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
    }
}

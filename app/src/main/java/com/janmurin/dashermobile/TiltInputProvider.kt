package com.janmurin.dashermobile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Reads the device's game-rotation-vector sensor and converts it into normalised 2-D
 * cursor coordinates that can be fed directly to [DasherSessionCoordinator.onTiltNormalized].
 *
 * ## Coordinate mapping
 * - **X axis** → device roll (left/right tilt) mapped to `[0, 1]`, neutral = `0.5`.
 * - **Y axis** → device pitch (forward/back tilt) mapped to `[0, 1]`, neutral = `0.5`.
 *
 * The mapping is relative to a *baseline* captured on the first sensor reading after
 * [calibrate] is called (or on construction).  This allows the user to hold the device
 * at any comfortable angle and treat that as the centre position.
 *
 * Exponential smoothing ([smoothingAlpha] ≈ 0.2) and a dead zone ([deadZoneRad]) reduce
 * jitter near the neutral position.
 *
 * @param context            Android context used to obtain the [SensorManager].
 * @param onTiltNormalized   Callback invoked with `(normalizedX, normalizedY)` each time a new
 *                           sensor sample is processed.  Called on the sensor-delivery thread.
 */
class TiltInputProvider(
    context: Context,
    private val onTiltNormalized: (Float, Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var isRegistered = false
    private var hasBaseline = false
    private var baselinePitch = 0f
    private var baselineRoll = 0f
    private var smoothedX = 0.5f
    private var smoothedY = 0.5f

    private val pitchRangeRad = 0.6f
    private val rollRangeRad = 0.6f
    private val deadZoneRad = 0.05f
    private val smoothingAlpha = 0.2f

    /**
     * Returns `true` if the device has a game-rotation-vector sensor and tilt input is usable.
     */
    fun hasSensor(): Boolean = rotationSensor != null

    /**
     * Registers the sensor listener and starts delivering tilt events.
     *
     * No-op if already registered or if no sensor is available.
     */
    fun register() {
        if (isRegistered || rotationSensor == null) return
        isRegistered = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    /**
     * Unregisters the sensor listener, stopping tilt event delivery.
     *
     * No-op if not currently registered.
     */
    fun unregister() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    /**
     * Resets the baseline so that the **next** sensor reading will be treated as the neutral
     * (centre) position.
     *
     * Call this after the user has settled the device into their preferred hold angle.
     */
    fun calibrate() {
        hasBaseline = false
    }

    /**
     * [SensorEventListener] callback – processes a new game-rotation-vector sample.
     *
     * Converts the rotation vector into pitch / roll angles, applies the dead zone and
     * exponential smoothing, then invokes [onTiltNormalized].
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val pitch = orientation[1]
        val roll = orientation[2]

        if (!hasBaseline) {
            baselinePitch = pitch
            baselineRoll = roll
            smoothedX = 0.5f
            smoothedY = 0.5f
            hasBaseline = true
        }

        val deltaPitch = applyDeadZone(pitch - baselinePitch)
        val deltaRoll = applyDeadZone(roll - baselineRoll)

        val targetX = (0.5f + (deltaRoll / rollRangeRad)).coerceIn(0f, 1f)
        val targetY = (0.5f - (deltaPitch / pitchRangeRad)).coerceIn(0f, 1f)

        smoothedX += (targetX - smoothedX) * smoothingAlpha
        smoothedY += (targetY - smoothedY) * smoothingAlpha

        onTiltNormalized(smoothedX.coerceIn(0f, 1f), smoothedY.coerceIn(0f, 1f))
    }

    /** Unused; required by [SensorEventListener]. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun applyDeadZone(value: Float): Float {
        val mag = abs(value)
        if (mag <= deadZoneRad) return 0f
        return if (value > 0f) mag - deadZoneRad else -(mag - deadZoneRad)
    }
}

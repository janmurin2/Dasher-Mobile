package com.janmurin.dashermobile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

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

    fun hasSensor(): Boolean = rotationSensor != null

    fun register() {
        if (isRegistered || rotationSensor == null) return
        isRegistered = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun unregister() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    fun calibrate() {
        hasBaseline = false
    }

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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun applyDeadZone(value: Float): Float {
        val mag = abs(value)
        if (mag <= deadZoneRad) return 0f
        return if (value > 0f) mag - deadZoneRad else -(mag - deadZoneRad)
    }
}


package com.janmurin.dashermobile

import android.content.Context

object DasherPrefs {
    const val PREFS_NAME = "dasher_mobile"
    const val KEY_LANGUAGE = "selected_language"
    const val KEY_LANGUAGE_MODEL = "selected_language_model"
    const val KEY_INPUT_MODE = "selected_input_mode"
    const val KEY_IME_HEIGHT_PERCENT = "ime_height_percent"
    const val KEY_MOVEMENT_SPEED_PERCENT = "movement_speed_percent"

    private const val DEFAULT_IME_HEIGHT_PERCENT = 40
    private const val DEFAULT_MOVEMENT_SPEED_PERCENT = 100

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguage(context: Context): DasherLanguage {
        val value = prefs(context).getString(KEY_LANGUAGE, DasherLanguage.ENGLISH.preferenceValue)
        return DasherLanguage.fromPreferenceValue(value)
    }

    fun setLanguage(context: Context, language: DasherLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.preferenceValue).apply()
    }

    fun getLanguageModel(context: Context): LanguageModel {
        val value = prefs(context).getString(KEY_LANGUAGE_MODEL, "ppm")
        return when (value) {
            "word" -> LanguageModel.WORD
            "kenlm" -> LanguageModel.KENLM
            else -> LanguageModel.PPM
        }
    }

    fun setLanguageModel(context: Context, model: LanguageModel) {
        val value = when (model) {
            LanguageModel.PPM -> "ppm"
            LanguageModel.WORD -> "word"
            LanguageModel.KENLM -> "kenlm"
        }
        prefs(context).edit().putString(KEY_LANGUAGE_MODEL, value).apply()
    }

    fun getInputMode(context: Context): InputMode {
        val value = prefs(context).getString(KEY_INPUT_MODE, "touch")
        return if (value == "tilt") InputMode.TILT else InputMode.TOUCH
    }

    fun setInputMode(context: Context, mode: InputMode) {
        val value = if (mode == InputMode.TILT) "tilt" else "touch"
        prefs(context).edit().putString(KEY_INPUT_MODE, value).apply()
    }

    fun getImeHeightPercent(context: Context): Int {
        val raw = prefs(context).getInt(KEY_IME_HEIGHT_PERCENT, DEFAULT_IME_HEIGHT_PERCENT)
        return normalizeImeHeightPercent(raw)
    }

    fun setImeHeightPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_IME_HEIGHT_PERCENT, normalizeImeHeightPercent(percent)).apply()
    }

    fun getMovementSpeedPercent(context: Context): Int {
        val raw = prefs(context).getInt(KEY_MOVEMENT_SPEED_PERCENT, DEFAULT_MOVEMENT_SPEED_PERCENT)
        return normalizeMovementSpeedPercent(raw)
    }

    fun setMovementSpeedPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_MOVEMENT_SPEED_PERCENT, normalizeMovementSpeedPercent(percent)).apply()
    }

    private fun normalizeImeHeightPercent(percent: Int): Int {
        val clamped = percent.coerceIn(30, 70)
        val roundedToStep = ((clamped + 5) / 10) * 10
        return roundedToStep.coerceIn(30, 70)
    }

    private fun normalizeMovementSpeedPercent(percent: Int): Int {
        val allowed = intArrayOf(50, 75, 100, 150, 200, 250, 300, 400)
        val clamped = percent.coerceIn(allowed.first(), allowed.last())
        var best = allowed.first()
        var bestDistance = kotlin.math.abs(clamped - best)
        for (i in 1 until allowed.size) {
            val candidate = allowed[i]
            val distance = kotlin.math.abs(clamped - candidate)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }
}


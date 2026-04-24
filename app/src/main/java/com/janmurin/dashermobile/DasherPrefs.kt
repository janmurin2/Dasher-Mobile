package com.janmurin.dashermobile

import android.content.Context

object DasherPrefs {
    const val PREFS_NAME = "dasher_mobile"
    const val KEY_LANGUAGE = "selected_language"
    const val KEY_LANGUAGE_MODEL = "selected_language_model"
    const val KEY_INPUT_MODE = "selected_input_mode"

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
}


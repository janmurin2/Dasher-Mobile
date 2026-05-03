package com.janmurin.dashermobile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object KenlmModelFiles {
    private const val MODEL_EXTENSION_BINARY = ".binary"
    private const val MODEL_EXTENSION_BIN = ".bin"

    private fun modelBaseName(language: DasherLanguage): String = "kenlm_${language.alphabetId}"

    fun expectedFileNames(language: DasherLanguage): List<String> {
        val base = modelBaseName(language)
        return listOf("$base$MODEL_EXTENSION_BINARY", "$base$MODEL_EXTENSION_BIN")
    }

    fun hasModel(context: Context, language: DasherLanguage): Boolean {
        return expectedFileNames(language).any { fileName ->
            File(context.filesDir, fileName).isFile
        }
    }

    private fun sourceDisplayName(context: Context, sourceUri: Uri): String? {
        context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        return sourceUri.lastPathSegment?.substringAfterLast('/')
    }

    fun hasValidModelFileName(context: Context, language: DasherLanguage, sourceUri: Uri): Boolean {
        val fileName = sourceDisplayName(context, sourceUri) ?: return false
        return expectedFileNames(language).any { expected ->
            expected.equals(fileName, ignoreCase = true)
        }
    }

    fun importModel(context: Context, language: DasherLanguage, sourceUri: Uri): Boolean {
        if (!hasValidModelFileName(context, language, sourceUri)) return false
        val input = context.contentResolver.openInputStream(sourceUri) ?: return false
        return try {
            val target = File(context.filesDir, "${modelBaseName(language)}$MODEL_EXTENSION_BINARY")
            input.use { source ->
                target.outputStream().use { output ->
                    source.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}


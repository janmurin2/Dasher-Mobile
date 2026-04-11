package com.janmurin.dashermobile

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class DasherImeTextSink(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?
) {
    private var lastText: String = ""

    fun resetTracking() {
        lastText = ""
    }

    fun onTextChanged(text: String) {
        if (text == lastText) {
            return
        }

        val inputConnection = inputConnectionProvider() ?: return
        val commonPrefixLength = commonPrefixLength(lastText, text)
        val deleteCount = lastText.length - commonPrefixLength
        val insertedText = text.substring(commonPrefixLength)

        inputConnection.beginBatchEdit()
        if (deleteCount > 0) {
            inputConnection.deleteSurroundingText(deleteCount, 0)
        }
        if (insertedText.isNotEmpty()) {
            commitInsertedText(inputConnection, insertedText)
        }
        inputConnection.endBatchEdit()
        lastText = text
    }

    private fun commitInsertedText(inputConnection: InputConnection, insertedText: String) {
        val chunk = StringBuilder()

        fun flushChunk() {
            if (chunk.isNotEmpty()) {
                inputConnection.commitText(chunk.toString(), 1)
                chunk.setLength(0)
            }
        }

        insertedText.forEach { ch ->
            when (ch) {
                ' ' -> {
                    flushChunk()
                    inputConnection.commitText(" ", 1)
                }
                '\n' -> {
                    flushChunk()
                    performEnterOrAction(inputConnection)
                }
                else -> chunk.append(ch)
            }
        }

        flushChunk()
    }

    private fun performEnterOrAction(inputConnection: InputConnection) {
        val editorInfo = editorInfoProvider()
        val imeOptions = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val inputType = editorInfo?.inputType ?: 0
        val isMultiline = inputType and (InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0

        if (!noEnterAction && !isMultiline && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            inputConnection.performEditorAction(action)
        } else {
            inputConnection.commitText("\n", 1)
        }
    }

    private fun commonPrefixLength(left: String, right: String): Int {
        val max = minOf(left.length, right.length)
        var index = 0
        while (index < max && left[index] == right[index]) {
            index++
        }
        return index
    }
}


package com.janmurin.dashermobile

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Bridges the DasherCore text output to an active [InputConnection].
 *
 * Instead of replacing the entire field text on every change, [DasherImeTextSink] computes the
 * **diff** between the previous output and the new output and sends only the minimal set of
 * [InputConnection.deleteSurroundingText] and [InputConnection.commitText] calls required to
 * bring the target field in sync.  This preserves the host app's undo history and is
 * significantly cheaper than re-inserting the full string each frame.
 *
 * ## IME action handling
 * When a newline character is output, [DasherImeTextSink] inspects the [EditorInfo] to decide
 * whether to commit a literal `\n` or to fire the editor's IME action (e.g. "Go", "Search",
 * "Send").
 *
 * @param inputConnectionProvider Lazy getter for the current [InputConnection].  May return
 *   `null` if the connection is no longer valid.
 * @param editorInfoProvider       Lazy getter for the current [EditorInfo].  Used to determine
 *   the correct behaviour for newline/enter output.
 */
class DasherImeTextSink(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?
) {
    private var lastText: String = ""

    /**
     * Resets the internal "last known text" snapshot.
     *
     * Should be called whenever the IME attaches to a new editor field so that the next
     * [onTextChanged] call computes its diff against an empty baseline.
     */
    fun resetTracking() {
        lastText = ""
    }

    /**
     * Called each time DasherCore's output buffer changes.
     *
     * Computes the diff between [text] and the previously seen output, then commits the
     * minimum required edits to the active [InputConnection].  No-op when [text] equals the
     * last seen value.
     *
     * @param text The full output text produced by DasherCore so far in this session.
     */
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

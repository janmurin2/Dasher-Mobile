#pragma once

#include "DasherCore/DashIntfScreenMsgs.h"
#include "DasherCore/XmlSettingsStore.h"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

class AndroidCommandScreen;
class AndroidPointerInput;

/**
 * @brief RAII wrapper that constructs and loads the XML settings store.
 *
 * Used as a base class so that @c XmlSettingsStore is fully initialised before
 * @c CDashIntfScreenMsgs accesses it during construction.
 */
struct AndroidSettingsHolder {
    std::unique_ptr<Dasher::XmlSettingsStore> settings;
    /**
     * @param settingsPath Absolute path to the XML settings file.
     *                     The file is created if it does not yet exist.
     */
    explicit AndroidSettingsHolder(const std::string &settingsPath);
};

/**
 * @brief Android-specific Dasher interface that renders into a command buffer.
 *
 * Combines @c AndroidSettingsHolder (for XML-backed settings) with
 * @c CDashIntfScreenMsgs (the portable DasherCore interface layer).  Each call to
 * @ref Frame collects the drawing primitives emitted by DasherCore into a flat
 * @c int32_t array that can be serialised across the JNI boundary and rendered on the
 * Java/Kotlin side by @c DasherCanvasView.
 *
 * ## Draw-command encoding
 * The array returned by @ref Frame is a flat sequence of 6-integer records:
 * @code
 *   [ op, a, b, c, d, argb ]
 * @endcode
 * | op | Primitive | Meaning of a–d |
 * |----|-----------|----------------|
 * | 0  | Clear     | (unused)       |
 * | 1  | Circle    | cx, cy, r, filled(0/1) |
 * | 2  | Line      | x1, y1, x2, y2 |
 * | 3  | Stroked rect | x1, y1, x2, y2 |
 * | 4  | Filled rect  | x1, y1, x2, y2 |
 * | 5  | Text      | x, y, fontSize, stringIndex |
 *
 * @note All public methods are called from the **main thread** only.
 */
class AndroidDasherInterface final
    : private AndroidSettingsHolder
    , public Dasher::CDashIntfScreenMsgs
{
public:
    /**
     * @param filesDir Absolute path to the app-private directory used for settings and model
     *                 cache files.
     */
    explicit AndroidDasherInterface(const std::string &filesDir);
    ~AndroidDasherInterface() override;

    /**
     * @brief Triggers DasherCore's frame-update logic.
     *
     * @param timeMs      Current time in milliseconds (monotonic clock).
     * @param forceRedraw Force a redraw even if the model has not changed.
     */
    void CallNewFrame(unsigned long timeMs, bool forceRedraw = false);

    /**
     * @brief Notifies DasherCore of the rendering surface dimensions.
     *
     * On the first call this also calls @c Realize() to complete engine initialisation and
     * applies any pending alphabet change.  Subsequent calls resize the screen and notify
     * the core.
     *
     * @param width  Surface width in pixels (must be > 0).
     * @param height Surface height in pixels (must be > 0).
     */
    void SetScreenSize(int width, int height);

    /**
     * @brief Delivers a touch event to the pointer-input module.
     *
     * Also handles the mouse-click start/stop protocol expected by DasherCore:
     * action 0 (DOWN) sends @c KeyDown, action 2 (UP) sends a second @c KeyDown.
     *
     * @param action 0 = touch down, 1 = move, 2 = up/cancel.
     * @param x      X coordinate in surface pixels.
     * @param y      Y coordinate in surface pixels.
     */
    void SetTouch(int action, float x, float y);

    /**
     * @brief Advances the Dasher model by one frame and returns draw commands.
     *
     * Calls @ref CallNewFrame internally and then drains the command buffer
     * from @c AndroidCommandScreen.
     *
     * @param timeMs Frame timestamp in milliseconds.
     * @return Flat draw-command array (multiples of 6 elements); empty if not yet realised.
     */
    std::vector<int32_t> Frame(long timeMs);

    /**
     * @brief Takes and clears the string labels queued during the last frame.
     *
     * Each @c op=5 draw command references an index into this list.
     *
     * @return Vector of UTF-8 label strings.  Cleared on each call.
     */
    std::vector<std::string> TakeFrameStrings();

    /**
     * @brief Returns the Dasher alphabet identifier currently in use.
     * @return Alphabet ID string (e.g. @c "English with limited punctuation").
     */
    std::string GetAlphabetId() const;

    /**
     * @brief Requests a Dasher alphabet change.
     *
     * If the engine has not been realised yet the change is deferred until @ref SetScreenSize
     * triggers @c Realize().
     *
     * @param alphabetId Target alphabet identifier; no-op if empty or already active.
     */
    void SetAlphabetId(const std::string &alphabetId);

    /**
     * @brief Returns the active language-model ID.
     * @return Value of @c LP_LANGUAGE_MODEL_ID (0 = PPM, 2 = Word, 5 = KenLM).
     */
    int GetLanguageModelId() const;

    /**
     * @brief Switches the language model.
     * @param modelId Accepted values: 0 (PPM), 2 (Word), 3, 4, 5 (KenLM).
     *                All other values are silently mapped to 0.
     */
    void SetLanguageModelId(int modelId);

    /**
     * @brief Returns the movement-speed multiplier as a percentage.
     *
     * The percentage is derived from @c LP_MAX_BITRATE relative to the baseline of 160 bits/s.
     *
     * @return Speed percentage in [20, 400].
     */
    int GetMovementSpeedPercent() const;

    /**
     * @brief Sets the movement-speed multiplier.
     * @param percent Desired speed percentage; clamped to [20, 400].
     */
    void SetMovementSpeedPercent(int percent);

    /// @name CDashIntfScreenMsgs overrides
    ///@{
    unsigned int ctrlMove(bool bForwards, Dasher::EditDistance dist) override;
    unsigned int ctrlDelete(bool bForwards, Dasher::EditDistance dist) override;
    void editOutput(const std::string &strText, Dasher::CDasherNode *pCause) override;
    void editDelete(const std::string &strText, Dasher::CDasherNode *pCause) override;
    std::string GetContext(unsigned int iStart, unsigned int iLength) override;
    std::string GetAllContext() override;
    int GetAllContextLenght() override;
    ///@}

    /**
     * @brief Returns the full accumulated output text.
     * @return The contents of the internal edit buffer.
     */
    std::string GetOutputText() const;

    /**
     * @brief Clears the internal edit buffer.
     */
    void ResetOutputText();

protected:
    /** @brief Registers the @c AndroidPointerInput module with DasherCore. */
    void CreateModules() override;

private:
    /// Accumulated output text buffer, updated by editOutput/editDelete.
    std::string m_editBuffer;
    /// Off-screen command-buffer renderer.
    std::unique_ptr<AndroidCommandScreen> m_screen;
    /// Non-owning pointer to the registered pointer input device.
    AndroidPointerInput *m_input = nullptr;
    /// True once Realize() has been called successfully.
    bool m_realized = false;
    /// True while a touch press is actively driving Dasher.
    bool m_startedByTouch = false;
    /// Alphabet ID to apply once the engine is realised.
    std::string m_pendingAlphabetId;
};

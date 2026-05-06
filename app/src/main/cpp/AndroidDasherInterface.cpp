/**
 * @file AndroidDasherInterface.cpp
 * @brief Android-specific Dasher rendering and input implementation.
 *
 * Contains:
 * - @c toArgb / @c isUsableColor – colour conversion helpers.
 * - @c AndroidPointerInput – translates touch coordinates into the @c CScreenCoordInput
 *   interface expected by DasherCore.
 * - @c AndroidCommandScreen – a @c CDasherScreen implementation that records every drawing
 *   call as a flat integer command buffer instead of rasterising pixels.  The buffer is
 *   transferred across the JNI boundary each frame and rendered in Kotlin by DasherCanvasView.
 * - @c AndroidDasherInterface – the top-level interface combining settings, screen and input.
 */

#include "AndroidDasherInterface.h"

#include "DasherCore/DasherInput.h"
#include "DasherCore/DasherScreen.h"
#include "DasherCore/ModuleManager.h"
#include "DasherCore/Parameters.h"

#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <utility>
#include <vector>

#define LOG_TAG "DasherInterface"

/**
 * @brief Converts a DasherCore colour to an ARGB packed integer.
 *
 * Handles both floating-point [0, 1] and integer [0, 255] colour component conventions.
 * Near-transparent alpha values (> 0 but < 24) are promoted to fully opaque to avoid
 * invisible rendering artefacts on the Android canvas.
 *
 * @param color DasherCore colour value.
 * @return 32-bit ARGB packed integer (A in bits 31–24, R in 23–16, G in 15–8, B in 7–0).
 */
static int32_t toArgb(const Dasher::ColorPalette::Color &color) {
    int a = color.Alpha;
    int r = color.Red;
    int g = color.Green;
    int b = color.Blue;

    const bool normalized = (a >= 0 && a <= 1) && (r >= 0 && r <= 1) && (g >= 0 && g <= 1) && (b >= 0 && b <= 1);
    if (normalized) {
        a *= 255;
        r *= 255;
        g *= 255;
        b *= 255;
    }

    a = std::clamp(a, 0, 255);
    r = std::clamp(r, 0, 255);
    g = std::clamp(g, 0, 255);
    b = std::clamp(b, 0, 255);

    if (a > 0 && a < 24) {
        a = 255;
    }

    return static_cast<int32_t>((a << 24) | (r << 16) | (g << 8) | b);
}

/**
 * @brief Returns @c true when a DasherCore colour is valid and non-transparent.
 *
 * Rejects colours with any negative component (sentinel "unset" value used by DasherCore)
 * and fully transparent colours (alpha == 0).
 *
 * @param color Colour to test.
 * @return @c true if the colour should be drawn.
 */
static bool isUsableColor(const Dasher::ColorPalette::Color &color) {
    if (color.Red < 0 || color.Green < 0 || color.Blue < 0 || color.Alpha < 0) {
        return false;
    }
    return color.Alpha > 0;
}

/**
 * @brief DasherCore input module that maps Android touch events to screen coordinates.
 *
 * Implements @c CScreenCoordInput so it can be registered with DasherCore's module manager.
 * Coordinates are clamped to the current surface bounds.  While no touch is active the
 * cursor is parked at the centre of the surface.
 */
class AndroidPointerInput : public Dasher::CScreenCoordInput {
public:
    AndroidPointerInput() : Dasher::CScreenCoordInput("Android Touch Input") {}

    /**
     * @brief Updates the known surface dimensions.
     *
     * When no touch is active the cursor is repositioned to the new centre.
     *
     * @param width  Surface width in pixels (clamped to a minimum of 1).
     * @param height Surface height in pixels (clamped to a minimum of 1).
     */
    void SetBounds(int width, int height) {
        m_width = std::max(1, width);
        m_height = std::max(1, height);
        if (!m_hasTouch) {
            m_x = m_width / 2;
            m_y = m_height / 2;
        }
    }

    /**
     * @brief Delivers a touch event and updates the current cursor position.
     *
     * @param action 0 = down (touch active), 1 = move, 2 = up/cancel (touch released).
     * @param x      Raw X coordinate; clamped to [0, width-1].
     * @param y      Raw Y coordinate; clamped to [0, height-1].
     */
    void SetTouch(int action, float x, float y) {
        if (action == 0) {
            m_hasTouch = true;
        } else if (action == 2) {
            m_hasTouch = false;
        }

        const int maxX = std::max(0, m_width - 1);
        const int maxY = std::max(0, m_height - 1);
        m_x = std::clamp(static_cast<int>(std::lround(x)), 0, maxX);
        m_y = std::clamp(static_cast<int>(std::lround(y)), 0, maxY);
    }

    /// @brief Returns the current clamped cursor position. Always returns @c true.
    bool GetScreenCoords(Dasher::screenint &iX, Dasher::screenint &iY, Dasher::CDasherView *) override {
        iX = static_cast<Dasher::screenint>(m_x);
        iY = static_cast<Dasher::screenint>(m_y);
        return true;
    }

private:
    int m_width = 1;    ///< Current surface width.
    int m_height = 1;   ///< Current surface height.
    int m_x = 0;        ///< Current cursor X (clamped).
    int m_y = 0;        ///< Current cursor Y (clamped).
    bool m_hasTouch = false; ///< @c true while a finger is down.
};

/**
 * @brief A @c CDasherScreen that records drawing calls as a flat integer command buffer.
 *
 * Instead of rasterising pixels, every drawing primitive is serialised as a 6-element
 * record @c [op, a, b, c, d, argb] appended to an internal @c std::vector<int32_t>.
 * The buffer is drained once per frame via @ref TakeCommands and sent across the JNI
 * boundary to the Kotlin rendering layer.
 *
 * String labels (op = 5) are stored in a parallel @c std::vector<std::string>; the @c d
 * field of the draw-command record carries the string's index into that vector.
 */
class AndroidCommandScreen final : public Dasher::CDasherScreen {
public:
    /**
     * @param width  Initial surface width in pixels.
     * @param height Initial surface height in pixels.
     */
    AndroidCommandScreen(int width, int height)
        : Dasher::CDasherScreen(static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height)) {}

    /**
     * @brief Resizes the screen and notifies DasherCore.
     * @param width  New width in pixels.
     * @param height New height in pixels.
     */
    void SetSize(int width, int height) {
        resize(static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height));
    }

    /**
     * @brief Clears the command and string buffers and emits a canvas-clear command.
     *
     * Must be called once at the start of each frame before DasherCore calls any draw methods.
     * The background colour used is @c #0D1117 (dark navy), fully opaque.
     */
    void BeginFrame() {
        m_commands.clear();
        m_strings.clear();
        push(0, 0, 0, 0, 0, static_cast<int32_t>(0xFF0D1117));
    }

    /**
     * @brief Returns and clears the accumulated draw-command buffer.
     * @return Flat command array (multiples of 6 elements).
     */
    std::vector<int32_t> TakeCommands() {
        return std::move(m_commands);
    }

    /**
     * @brief Returns and clears the accumulated string label buffer.
     * @return Strings in the order they were emitted by @c DrawString.
     */
    std::vector<std::string> TakeStrings() {
        return std::move(m_strings);
    }

    /**
     * @brief Estimates the rendered size of a text label.
     *
     * Uses a simple proportional approximation (width = char count * fontSize / 2) since
     * the actual font metrics are unknown at the native layer.
     */
    std::pair<Dasher::screenint, Dasher::screenint> TextSize(Label *label, unsigned int iFontSize) override {
        if (!label) return {0, 0};
        const int width = static_cast<int>(label->m_strText.size()) * static_cast<int>(iFontSize) / 2;
        const int height = static_cast<int>(iFontSize);
        return {static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height)};
    }

    void DrawString(Label *label,
                    Dasher::screenint x,
                    Dasher::screenint y,
                    unsigned int iFontSize,
                    const Dasher::ColorPalette::Color &color) override {
        if (!label || label->m_strText.empty() || iFontSize == 0) return;
        const int idx = static_cast<int>(m_strings.size());
        m_strings.push_back(label->m_strText);
        push(5, static_cast<int>(x), static_cast<int>(y), static_cast<int>(iFontSize), idx, toArgb(color));

        static int s_logCounter = 0;
        if (++s_logCounter % 360 == 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "DasherInterface",
                                "DrawString: \"%s\" x=%d y=%d size=%u",
                                label->m_strText.c_str(),
                                static_cast<int>(x), static_cast<int>(y), iFontSize);
        }
    }

    /**
     * @brief Records a rectangle draw command (filled and/or outlined).
     *
     * Emits op=4 (filled rect) when @p color is usable, and op=3 (stroked rect)
     * when @p outlineColor is usable and @p iThickness > 0.
     */
    void DrawRectangle(Dasher::screenint x1,
                       Dasher::screenint y1,
                       Dasher::screenint x2,
                       Dasher::screenint y2,
                       const Dasher::ColorPalette::Color &color,
                       const Dasher::ColorPalette::Color &outlineColor,
                       int iThickness) override {
        if (isUsableColor(color)) {
            push(4, x1, y1, x2, y2, toArgb(color));
        }
        if (iThickness > 0 && isUsableColor(outlineColor)) {
            push(3, x1, y1, x2, y2, toArgb(outlineColor));
        }
    }

    /**
     * @brief Records a circle draw command (filled and/or outlined).
     *
     * Emits op=1 with @c d=1 for a filled circle and op=1 with @c d=0 for an outline circle.
     */
    void DrawCircle(Dasher::screenint iCX,
                    Dasher::screenint iCY,
                    Dasher::screenint iR,
                    const Dasher::ColorPalette::Color &fillColor,
                    const Dasher::ColorPalette::Color &lineColor,
                    int iLineWidth) override {
        if (isUsableColor(fillColor)) {
            push(1, iCX, iCY, iR, 1, toArgb(fillColor));
        }
        if (iLineWidth > 0 && isUsableColor(lineColor)) {
            push(1, iCX, iCY, iR, 0, toArgb(lineColor));
        }
    }

    /**
     * @brief Records a polyline as a sequence of op=2 (line) commands.
     *
     * @param Points Pointer to an array of @p Number points.
     * @param Number Number of points; must be ≥ 2 to emit any commands.
     */
    void Polyline(Dasher::point *Points,
                  int Number,
                  int,
                  const Dasher::ColorPalette::Color &color = {255, 255, 255}) override {
        if (!Points || Number < 2 || !isUsableColor(color)) return;
        const int32_t argb = toArgb(color);
        for (int i = 1; i < Number; ++i) {
            push(2, Points[i - 1].x, Points[i - 1].y, Points[i].x, Points[i].y, argb);
        }
    }

    /**
     * @brief Records a polygon outline as a sequence of op=2 (line) commands.
     *
     * Only the outline is drawn (fill colour is ignored).  The closing edge from the last
     * point back to the first is added automatically.
     */
    void Polygon(Dasher::point *Points,
                 int Number,
                 const Dasher::ColorPalette::Color &,
                 const Dasher::ColorPalette::Color &outlineColor,
                 int lineWidth) override {
        if (!Points || Number < 2 || lineWidth <= 0 || !isUsableColor(outlineColor)) return;
        const int32_t argb = toArgb(outlineColor);
        for (int i = 1; i < Number; ++i) {
            push(2, Points[i - 1].x, Points[i - 1].y, Points[i].x, Points[i].y, argb);
        }
        push(2, Points[Number - 1].x, Points[Number - 1].y, Points[0].x, Points[0].y, argb);
    }

    /// @brief No-op: the command buffer is already "displayed" by being drained each frame.
    void Display() override {}

    /// @brief Always returns @c true; clipping is handled on the Kotlin side.
    bool IsPointVisible(Dasher::screenint, Dasher::screenint) override {
        return true;
    }

private:
    /**
     * @brief Appends a 6-element draw-command record to @ref m_commands.
     *
     * @param op    Primitive opcode (0–5).
     * @param a–d   Primitive parameters (interpretation depends on @p op).
     * @param color Packed ARGB colour from @ref toArgb.
     */
    void push(int op, int a, int b, int c, int d, int32_t color) {
        m_commands.push_back(op);
        m_commands.push_back(a);
        m_commands.push_back(b);
        m_commands.push_back(c);
        m_commands.push_back(d);
        m_commands.push_back(color);
    }

    std::vector<int32_t> m_commands;   ///< Flat draw-command buffer, drained each frame.
    std::vector<std::string> m_strings; ///< String label buffer, drained each frame.
};

namespace {

/**
 * @brief Returns the current time as a millisecond timestamp (monotonic clock).
 * @return Milliseconds since an arbitrary epoch, suitable for passing to DasherCore.
 */
unsigned long nowMs() {
    const auto now = std::chrono::steady_clock::now().time_since_epoch();
    return static_cast<unsigned long>(std::chrono::duration_cast<std::chrono::milliseconds>(now).count());
}

}

AndroidSettingsHolder::AndroidSettingsHolder(const std::string &settingsPath) {
    settings = std::make_unique<Dasher::XmlSettingsStore>(settingsPath, nullptr);
    settings->Load();
}

AndroidDasherInterface::AndroidDasherInterface(const std::string &filesDir)
    : AndroidSettingsHolder(filesDir + "/dasher_settings.xml")
    , Dasher::CDashIntfScreenMsgs(AndroidSettingsHolder::settings.get())
{
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "created (filesDir=%s)", filesDir.c_str());
    __android_log_print(ANDROID_LOG_INFO,
                        LOG_TAG,
                        "inputFilter=%s",
                        GetStringParameter(Dasher::SP_INPUT_FILTER).c_str());
}

AndroidDasherInterface::~AndroidDasherInterface() = default;

void AndroidDasherInterface::CallNewFrame(unsigned long timeMs, bool forceRedraw) {
    NewFrame(timeMs, forceRedraw);
}

void AndroidDasherInterface::CreateModules() {
    Dasher::CDashIntfScreenMsgs::CreateModules();
    auto input = std::make_unique<AndroidPointerInput>();
    m_input = input.get();
    GetModuleManager()->RegisterInputDeviceModule(std::move(input), true);
}

void AndroidDasherInterface::SetScreenSize(int width, int height) {
    if (width <= 0 || height <= 0) {
        return;
    }

    if (!m_screen) {
        m_screen = std::make_unique<AndroidCommandScreen>(width, height);
        ChangeScreen(m_screen.get());
    } else {
        m_screen->SetSize(width, height);
        ScreenResized(m_screen.get());
    }

    if (!m_realized) {
        Realize(nowMs());
        m_realized = true;
        if (!m_pendingAlphabetId.empty()) {
            const std::string pendingAlphabet = m_pendingAlphabetId;
            m_pendingAlphabetId.clear();
            SetAlphabetId(pendingAlphabet);
        }
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "alphabetID=%s",
                            GetStringParameter(Dasher::SP_ALPHABET_ID).c_str());
    }

    if (m_input) {
        m_input->SetBounds(width, height);
    }
}

void AndroidDasherInterface::SetTouch(int action, float x, float y) {
    if (!m_input) {
        return;
    }
    m_input->SetTouch(action, x, y);

    if (action == 0 && !m_startedByTouch) {
        SetBoolParameter(Dasher::BP_START_MOUSE, true);
        KeyDown(nowMs(), Dasher::Keys::Primary_Input);
        m_startedByTouch = true;
    } else if (action == 2 && m_startedByTouch) {
        KeyDown(nowMs(), Dasher::Keys::Primary_Input);
        m_startedByTouch = false;
    }
}

std::vector<int32_t> AndroidDasherInterface::Frame(long timeMs) {
    if (!m_realized || !m_screen) {
        return {};
    }

    m_screen->BeginFrame();
    CallNewFrame(static_cast<unsigned long>(std::max(0L, timeMs)), false);
    return m_screen->TakeCommands();
}

std::vector<std::string> AndroidDasherInterface::TakeFrameStrings() {
    if (!m_screen) return {};
    return m_screen->TakeStrings();
}

std::string AndroidDasherInterface::GetAlphabetId() const {
    return GetStringParameter(Dasher::SP_ALPHABET_ID);
}

void AndroidDasherInterface::SetAlphabetId(const std::string &alphabetId) {
    if (alphabetId.empty()) {
        return;
    }

    m_editBuffer.clear();

    if (!m_realized) {
        m_pendingAlphabetId = alphabetId;
        return;
    }

    if (GetStringParameter(Dasher::SP_ALPHABET_ID) == alphabetId) {
        return;
    }

    if (m_startedByTouch) {
        KeyDown(nowMs(), Dasher::Keys::Primary_Input);
        m_startedByTouch = false;
    }

    SetStringParameter(Dasher::SP_ALPHABET_ID, alphabetId);
}

int AndroidDasherInterface::GetLanguageModelId() const {
    return static_cast<int>(GetLongParameter(Dasher::LP_LANGUAGE_MODEL_ID));
}

void AndroidDasherInterface::SetLanguageModelId(int modelId) {
    const int resolved = (modelId == 0 || modelId == 2 || modelId == 3 || modelId == 4 || modelId == 5) ? modelId : 0;
    if (GetLongParameter(Dasher::LP_LANGUAGE_MODEL_ID) == static_cast<long>(resolved)) {
        return;
    }
    if (m_startedByTouch) {
        KeyDown(nowMs(), Dasher::Keys::Primary_Input);
        m_startedByTouch = false;
    }
    SetLongParameter(Dasher::LP_LANGUAGE_MODEL_ID, static_cast<long>(resolved));
}

int AndroidDasherInterface::GetMovementSpeedPercent() const {
    constexpr double kBaseBitrate = 160.0;
    const auto bitrate = static_cast<double>(GetLongParameter(Dasher::LP_MAX_BITRATE));
    const int percent = static_cast<int>(std::lround((bitrate / kBaseBitrate) * 100.0));
    return std::clamp(percent, 20, 400);
}

void AndroidDasherInterface::SetMovementSpeedPercent(int percent) {
    constexpr double kBaseBitrate = 160.0;
    const int resolvedPercent = std::clamp(percent, 20, 400);
    const long requested = static_cast<long>(std::lround((resolvedPercent / 100.0) * kBaseBitrate));
    const long bitrate = std::max(1L, requested);
    if (GetLongParameter(Dasher::LP_MAX_BITRATE) == bitrate) {
        return;
    }
    SetLongParameter(Dasher::LP_MAX_BITRATE, bitrate);
}

unsigned int AndroidDasherInterface::ctrlMove(bool, Dasher::EditDistance) {
    return static_cast<unsigned int>(m_editBuffer.size());
}

unsigned int AndroidDasherInterface::ctrlDelete(bool, Dasher::EditDistance) {
    return static_cast<unsigned int>(m_editBuffer.size());
}

void AndroidDasherInterface::editOutput(const std::string &strText, Dasher::CDasherNode *pCause) {
    m_editBuffer += strText;
    Dasher::CDashIntfScreenMsgs::editOutput(strText, pCause);
}

void AndroidDasherInterface::editDelete(const std::string &strText, Dasher::CDasherNode *pCause) {
    if (!strText.empty() && m_editBuffer.size() >= strText.size()) {
        m_editBuffer.erase(m_editBuffer.size() - strText.size());
    }
    Dasher::CDashIntfScreenMsgs::editDelete(strText, pCause);
}

std::string AndroidDasherInterface::GetContext(unsigned int iStart, unsigned int iLength) {
    if (iStart >= m_editBuffer.size()) return {};
    return m_editBuffer.substr(iStart, iLength);
}

std::string AndroidDasherInterface::GetAllContext() {
    return m_editBuffer;
}

int AndroidDasherInterface::GetAllContextLenght() {
    return static_cast<int>(m_editBuffer.size());
}

std::string AndroidDasherInterface::GetOutputText() const {
    return m_editBuffer;
}

void AndroidDasherInterface::ResetOutputText() {
    m_editBuffer.clear();
}


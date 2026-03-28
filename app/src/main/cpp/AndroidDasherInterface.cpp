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

static bool isUsableColor(const Dasher::ColorPalette::Color &color) {
    if (color.Red < 0 || color.Green < 0 || color.Blue < 0 || color.Alpha < 0) {
        return false;
    }
    return color.Alpha > 0;
}

class AndroidPointerInput : public Dasher::CScreenCoordInput {
public:
    AndroidPointerInput() : Dasher::CScreenCoordInput("Android Touch Input") {}

    void SetBounds(int width, int height) {
        m_width = std::max(1, width);
        m_height = std::max(1, height);
        if (!m_hasTouch) {
            m_x = m_width / 2;
            m_y = m_height / 2;
        }
    }

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

    bool GetScreenCoords(Dasher::screenint &iX, Dasher::screenint &iY, Dasher::CDasherView *) override {
        iX = static_cast<Dasher::screenint>(m_x);
        iY = static_cast<Dasher::screenint>(m_y);
        return true;
    }

private:
    int m_width = 1;
    int m_height = 1;
    int m_x = 0;
    int m_y = 0;
    bool m_hasTouch = false;
};

class AndroidCommandScreen final : public Dasher::CDasherScreen {
public:
    AndroidCommandScreen(int width, int height)
        : Dasher::CDasherScreen(static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height)) {}

    void SetSize(int width, int height) {
        resize(static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height));
    }

    void BeginFrame() {
        m_commands.clear();
        push(0, 0, 0, 0, 0, static_cast<int32_t>(0xFF0D1117));
    }

    std::vector<int32_t> TakeCommands() {
        return std::move(m_commands);
    }

    std::pair<Dasher::screenint, Dasher::screenint> TextSize(Label *label, unsigned int iFontSize) override {
        if (!label) return {0, 0};
        const int width = static_cast<int>(label->m_strText.size()) * static_cast<int>(iFontSize) / 2;
        const int height = static_cast<int>(iFontSize);
        return {static_cast<Dasher::screenint>(width), static_cast<Dasher::screenint>(height)};
    }

    void DrawString(Label *, Dasher::screenint, Dasher::screenint, unsigned int, const Dasher::ColorPalette::Color &) override {}

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

    void Display() override {}

    bool IsPointVisible(Dasher::screenint, Dasher::screenint) override {
        return true;
    }

private:
    void push(int op, int a, int b, int c, int d, int32_t color) {
        m_commands.push_back(op);
        m_commands.push_back(a);
        m_commands.push_back(b);
        m_commands.push_back(c);
        m_commands.push_back(d);
        m_commands.push_back(color);
    }

    std::vector<int32_t> m_commands;
};

namespace {

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

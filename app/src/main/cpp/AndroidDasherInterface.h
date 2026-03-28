#pragma once

#include "DasherCore/DashIntfScreenMsgs.h"
#include "DasherCore/XmlSettingsStore.h"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

class AndroidCommandScreen;
class AndroidPointerInput;

struct AndroidSettingsHolder {
    std::unique_ptr<Dasher::XmlSettingsStore> settings;
    explicit AndroidSettingsHolder(const std::string &settingsPath);
};

class AndroidDasherInterface final
    : private AndroidSettingsHolder
    , public Dasher::CDashIntfScreenMsgs
{
public:
    explicit AndroidDasherInterface(const std::string &filesDir);
    ~AndroidDasherInterface() override;

    void CallNewFrame(unsigned long timeMs, bool forceRedraw = false);
    void SetScreenSize(int width, int height);
    void SetTouch(int action, float x, float y);
    std::vector<int32_t> Frame(long timeMs);

    unsigned int ctrlMove(bool bForwards, Dasher::EditDistance dist) override;
    unsigned int ctrlDelete(bool bForwards, Dasher::EditDistance dist) override;
    void editOutput(const std::string &strText, Dasher::CDasherNode *pCause) override;
    void editDelete(const std::string &strText, Dasher::CDasherNode *pCause) override;

    std::string GetContext(unsigned int iStart, unsigned int iLength) override;
    std::string GetAllContext() override;
    int GetAllContextLenght() override;

protected:
    void CreateModules() override;

private:
    std::string m_editBuffer;
    std::unique_ptr<AndroidCommandScreen> m_screen;
    AndroidPointerInput *m_input = nullptr;
    bool m_realized = false;
    bool m_startedByTouch = false;
};

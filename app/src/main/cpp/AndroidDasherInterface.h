#pragma once

#include "DasherCore/DashIntfScreenMsgs.h"
#include "DasherCore/XmlSettingsStore.h"

#include <memory>
#include <string>

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
    ~AndroidDasherInterface() override = default;

    void CallNewFrame(unsigned long timeMs, bool forceRedraw = false);

    unsigned int ctrlMove(bool bForwards, Dasher::EditDistance dist) override;
    unsigned int ctrlDelete(bool bForwards, Dasher::EditDistance dist) override;

    std::string GetContext(unsigned int iStart, unsigned int iLength) override;
    std::string GetAllContext() override;
    int GetAllContextLenght() override;

private:
    std::string m_editBuffer;
};

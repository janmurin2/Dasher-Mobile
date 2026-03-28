#include "AndroidDasherInterface.h"
#include <android/log.h>

#define LOG_TAG "DasherInterface"

AndroidSettingsHolder::AndroidSettingsHolder(const std::string &settingsPath) {
    settings = std::make_unique<Dasher::XmlSettingsStore>(settingsPath, nullptr);
    settings->Load();
}

AndroidDasherInterface::AndroidDasherInterface(const std::string &filesDir)
    : AndroidSettingsHolder(filesDir + "/dasher_settings.xml")
    , Dasher::CDashIntfScreenMsgs(AndroidSettingsHolder::settings.get())
{
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "created (filesDir=%s)", filesDir.c_str());
}

void AndroidDasherInterface::CallNewFrame(unsigned long timeMs, bool forceRedraw) {
    NewFrame(timeMs, forceRedraw);
}

unsigned int AndroidDasherInterface::ctrlMove(bool, Dasher::EditDistance) {
    return static_cast<unsigned int>(m_editBuffer.size());
}

unsigned int AndroidDasherInterface::ctrlDelete(bool, Dasher::EditDistance) {
    return static_cast<unsigned int>(m_editBuffer.size());
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

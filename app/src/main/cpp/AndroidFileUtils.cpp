#include "DasherCore/FileUtils.h"

#include <android/log.h>
#include <fstream>
#include <string>

#define LOG_TAG "DasherFileUtils"

int Dasher::FileUtils::GetFileSize(const std::string &strFileName) {
    std::ifstream f(strFileName, std::ios::binary | std::ios::ate);
    if (!f.is_open()) return 0;
    return static_cast<int>(f.tellg());
}

void Dasher::FileUtils::ScanFiles(AbstractParser *, const std::string &) {}

bool Dasher::FileUtils::WriteUserDataFile(const std::string &filename,
                                           const std::string &strNewText,
                                           bool append) {
    std::ofstream f(filename, append ? std::ios::app : std::ios::trunc);
    if (!f.is_open()) return false;
    f << strNewText;
    return f.good();
}

std::string Dasher::FileUtils::GetFullFilenamePath(const std::string strFilename) {
    return strFilename;
}

#include "DasherCore/FileUtils.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <fstream>
#include <ios>
#include <regex>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "DasherFileUtils"

namespace {

AAssetManager *g_assetManager = nullptr;
std::string g_userDataDir;

std::string joinPath(const std::string &a, const std::string &b) {
    if (a.empty()) return b;
    if (a.back() == '/' || a.back() == '\\') return a + b;
    return a + "/" + b;
}

std::string resolvePath(const std::string &path) {
    if (path.empty()) return path;
    if (path[0] == '/' || (path.size() > 1 && path[1] == ':')) {
        return path;
    }
    if (g_userDataDir.empty()) {
        return path;
    }
    return joinPath(g_userDataDir, path);
}

bool parseAsset(AbstractParser *parser, const std::string &assetPath) {
    if (!g_assetManager || !parser) return false;
    AAsset *asset = AAssetManager_open(g_assetManager, assetPath.c_str(), AASSET_MODE_BUFFER);
    if (!asset) return false;

    const off_t len = AAsset_getLength(asset);
    if (len <= 0) {
        AAsset_close(asset);
        return false;
    }

    std::string data;
    data.resize(static_cast<size_t>(len));
    const int read = AAsset_read(asset, data.data(), static_cast<size_t>(len));
    AAsset_close(asset);
    if (read <= 0) return false;

    std::istringstream stream(data);
    const bool ok = parser->Parse(assetPath, stream, false);
    if (ok) {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "parsed asset: %s (%d bytes)", assetPath.c_str(), read);
    }
    return ok;
}

void scanAssetDir(AbstractParser *parser,
                  const std::string &dir,
                  const std::regex &pattern) {
    if (!g_assetManager || !parser) return;
    AAssetDir *assetDir = AAssetManager_openDir(g_assetManager, dir.c_str());
    if (!assetDir) return;

    const char *entry = nullptr;
    while ((entry = AAssetDir_getNextFileName(assetDir)) != nullptr) {
        std::string fileName(entry);
        if (!std::regex_search(fileName, pattern)) continue;
        const std::string path = dir.empty() ? fileName : joinPath(dir, fileName);
        parseAsset(parser, path);
    }

    AAssetDir_close(assetDir);
}

}

namespace DasherAndroid {

void SetAssetManager(AAssetManager *assetManager) {
    g_assetManager = assetManager;
}

void SetUserDataDir(const std::string &userDataDir) {
    g_userDataDir = userDataDir;
}

}

int Dasher::FileUtils::GetFileSize(const std::string &strFileName) {
    std::ifstream f(resolvePath(strFileName), std::ios::binary | std::ios::ate);
    if (!f.is_open()) return 0;
    return static_cast<int>(f.tellg());
}

void Dasher::FileUtils::ScanFiles(AbstractParser *parser, const std::string &strPattern) {
    if (!parser) return;

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "ScanFiles: pattern=%s", strPattern.c_str());

    std::ifstream local(resolvePath(strPattern), std::ios::binary);
    if (local.is_open()) {
        local.close();
        const std::string localPath = resolvePath(strPattern);
        const bool parsedLocal = parser->ParseFile(localPath, true);
        __android_log_print(ANDROID_LOG_DEBUG,
                            LOG_TAG,
                            "parsed local: %s ok=%d",
                            localPath.c_str(),
                            parsedLocal ? 1 : 0);
    }

    std::regex pattern(strPattern);
    scanAssetDir(parser, "", pattern);
    scanAssetDir(parser, "alphabets", pattern);
    scanAssetDir(parser, "colors", pattern);
    scanAssetDir(parser, "settings", pattern);
    scanAssetDir(parser, "training", pattern);
}

bool Dasher::FileUtils::WriteUserDataFile(const std::string &filename,
                                           const std::string &strNewText,
                                           bool append) {
    std::ofstream f(resolvePath(filename), append ? std::ios::app : std::ios::trunc);
    if (!f.is_open()) return false;
    f << strNewText;
    return f.good();
}

std::string Dasher::FileUtils::GetFullFilenamePath(const std::string strFilename) {
    return resolvePath(strFilename);
}

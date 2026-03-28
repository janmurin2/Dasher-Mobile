cmake_minimum_required(VERSION 3.22.1)
project("dasher_jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(DASHER_HAVE_OWN_FILEUTILS ON CACHE BOOL "" FORCE)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/DasherCore
    ${CMAKE_BINARY_DIR}/DasherCore
    EXCLUDE_FROM_ALL
)

target_include_directories(DasherCore PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/android_compat
)

add_library(dasher_jni SHARED
    AndroidFileUtils.cpp
    AndroidDasherInterface.cpp
    jni_bridge.cpp
)

find_library(log-lib log)

target_link_libraries(dasher_jni
    DasherCore
    ${log-lib}
)

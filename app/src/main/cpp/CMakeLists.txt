cmake_minimum_required(VERSION 3.22.1)
project("dasher_jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(DASHER_HAVE_OWN_FILEUTILS ON CACHE BOOL "" FORCE)

include(${CMAKE_CURRENT_SOURCE_DIR}/kenlm_query_only.cmake)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/DasherCore
    ${CMAKE_BINARY_DIR}/DasherCore
    EXCLUDE_FROM_ALL
)

target_include_directories(DasherCore PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/android_compat
)

target_compile_definitions(DasherCore PUBLIC HAVE_KENLM)
target_include_directories(DasherCore PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/kenlm
)
target_link_libraries(DasherCore kenlm_query)

add_library(dasher_jni SHARED
    AndroidFileUtils.cpp
    AndroidDasherInterface.cpp
    jni_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(dasher_jni
    DasherCore
    kenlm_query
    ${android-lib}
    ${log-lib}
)

cmake_minimum_required(VERSION 3.22.1)
project(whisper-jni)

include(FetchContent)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# whisper.cpp v1.7.4: on-device multilingual ASR + translation (spec 0010 V1).
FetchContent_Declare(
    whispercpp
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG v1.7.4
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper-jni SHARED whisper_jni.c)

target_link_libraries(whisper-jni whisper log)
target_include_directories(whisper-jni PRIVATE
    ${whispercpp_SOURCE_DIR}/include
    ${whispercpp_SOURCE_DIR}/ggml/include
)
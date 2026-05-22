cmake_minimum_required(VERSION 3.22.1)
project(metronome CXX)

find_package(oboe REQUIRED CONFIG)

add_library(metronome SHARED
    MetronomeEngine.cpp
    jni_bridge.cpp
)

target_include_directories(metronome PRIVATE .)

target_compile_options(metronome PRIVATE -std=c++17 -Wall -Wextra)

target_link_libraries(metronome
    oboe::oboe
    android
    log
)

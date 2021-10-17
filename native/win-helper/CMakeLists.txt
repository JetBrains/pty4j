cmake_minimum_required(VERSION 3.4.0)
project(win-helper)

if(NOT WIN32)
    message(FATAL_ERROR "Windows only.")
endif()

if(TARGET_X64)
    message("Target x64")
else()
    message("Target NOT x64")
endif()

set(SOURCE_FILES win-helper.cpp)

add_library(win-helper SHARED ${SOURCE_FILES})

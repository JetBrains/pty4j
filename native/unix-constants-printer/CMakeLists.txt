cmake_minimum_required(VERSION 3.20)
project(unix-constants-printer C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")

add_compile_options("-fmodules" "-Wall" "-Wextra" "-Wpedantic" "-Wno-newline-eof")

add_executable(unix-constants-printer main.c main.c)

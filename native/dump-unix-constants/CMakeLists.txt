cmake_minimum_required(VERSION 3.20)
project(dump-unix-constants C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")

add_compile_options("-fmodules" "-Wall" "-Wextra" "-Wpedantic" "-Wno-newline-eof")

add_executable(dump-unix-constants main.c main.c)

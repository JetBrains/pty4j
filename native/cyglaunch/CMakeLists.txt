cmake_minimum_required(VERSION 2.8.4)
project(cyglaunch)

set(SOURCE_FILES ../exec_pty.c ../exec_pty.h ../openpty.c ../pfind.c main.c)
add_executable(cyglaunch ${SOURCE_FILES})
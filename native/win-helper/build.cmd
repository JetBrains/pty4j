@ECHO ON

rem For Visual Studio 15 2017, platform can be Win32/x64/ARM/ARM64
rem https://cmake.org/cmake/help/latest/generator/Visual%20Studio%2015%202017.html

SET PLATFORM=%1
SET CMAKE_SYSTEM_VERSION=%2
SET BUILD_DIR="%~dp0\build-release-%PLATFORM%"

IF EXIST "%BUILD_DIR%" RMDIR /S /Q "%BUILD_DIR%"
MKDIR "%BUILD_DIR%" & CD "%BUILD_DIR%"

cmake -G "Visual Studio 15 2017" -T v141 -A "%PLATFORM%" -DCMAKE_SYSTEM_VERSION="%CMAKE_SYSTEM_VERSION%" ..
IF ERRORLEVEL 1 EXIT 1

cmake --build . --config Release
IF ERRORLEVEL 1 EXIT 2

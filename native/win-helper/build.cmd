@ECHO ON

rem For Visual Studio 16 2019, platform can be Win32/x64/ARM/ARM64
rem https://cmake.org/cmake/help/latest/generator/Visual%20Studio%2016%202019.html

SET PLATFORM=%1
SET BUILD_DIR="%~dp0\build-release-%PLATFORM%"
SET CMAKE=C:\Users\segrey\programs\cmakes\cmake-3.21.2-windows-x86_64\bin\cmake.exe

IF EXIST "%BUILD_DIR%" RMDIR /S /Q "%BUILD_DIR%"
MKDIR "%BUILD_DIR%" & CD "%BUILD_DIR%"

"%CMAKE%" -G "Visual Studio 16 2019" -T v141 -A "%PLATFORM%" ..
IF ERRORLEVEL 1 EXIT 1

"%CMAKE%" --build . --config Release
IF ERRORLEVEL 1 EXIT 2

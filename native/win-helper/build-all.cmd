@ECHO ON

call build.cmd Win32 6.1
IF ERRORLEVEL 1 EXIT 1
call build.cmd x64   6.1
IF ERRORLEVEL 1 EXIT 1
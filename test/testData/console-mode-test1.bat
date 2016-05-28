@echo off
setlocal

REM -- Write abcdefz to stdout and ABCDEFZ to stderr, pausing one second
REM -- between each three characters.

REM -- This script uses set /p to output text without a newline.  See
REM -- http://stackoverflow.com/a/7105690.  It uses ping -n 2 instead of
REM -- timeout.exe /t 1, because timeout.exe actually waits between 0-1
REM -- seconds.

<NUL set /p dummy=abc
<NUL set /p dummy=ABC>&2
ping -n 2 127.0.0.1 >NUL

<NUL set /p dummy=def
<NUL set /p dummy=DEF>&2
ping -n 2 127.0.0.1 >NUL

echo z
echo Z>&2

echo off

echo Current directory is %cd%
set /p new_working_dir="cd to new directory:"
cd "%new_working_dir%"
echo New current directory is %cd%
set /p done="Press Enter to exit"

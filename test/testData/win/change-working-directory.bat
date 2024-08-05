echo off

echo Working directory is %cd%
set /p new_working_dir="Enter new working directory:"

cd "%new_working_dir%"

echo Working directory is %cd%
set /p done="Press Enter to exit"

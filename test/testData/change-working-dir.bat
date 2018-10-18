echo off
set timeout_before_chdir=%1
set new_working_dir=%2
set timeout_before_exit=%3

echo Current directory is %cd%
timeout /T "%timeout_before_chdir%" /NOBREAK
cd "%new_working_dir%"
echo New current directory is %cd%, will exit in %timeout_before_exit% sec
timeout /T "%timeout_before_exit%" /NOBREAK

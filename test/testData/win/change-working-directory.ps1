[Console]::WriteLine("Working directory is $((Get-Location).Path)")
$newWorkingDir = Read-Host "Enter new working directory"

Set-Location -Path "$newWorkingDir"

[Console]::WriteLine("Working directory is $((Get-Location).Path)")
$newWorkingDir = Read-Host "Enter anything"

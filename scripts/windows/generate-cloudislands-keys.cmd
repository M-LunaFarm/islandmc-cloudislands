@echo off
setlocal EnableExtensions

set "CI_KEYS_OUTPUT=%~1"
if not defined CI_KEYS_OUTPUT set "CI_KEYS_OUTPUT=%CD%\cloudislands-secrets"
set "CI_KEYS_FORCE=0"
if /I "%~2"=="--force" set "CI_KEYS_FORCE=1"

echo CloudIslands secure key generator
echo Output: %CI_KEYS_OUTPUT%
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$output=[IO.Path]::GetFullPath($env:CI_KEYS_OUTPUT);" ^
  "$force=$env:CI_KEYS_FORCE -eq '1';" ^
  "[IO.Directory]::CreateDirectory($output) | Out-Null;" ^
  "$rng=[Security.Cryptography.RandomNumberGenerator]::Create();" ^
  "$utf8=New-Object Text.UTF8Encoding($false);" ^
  "foreach($name in @('core-token','admin-token','forwarding.secret')){" ^
  "  $path=Join-Path $output $name;" ^
  "  if((-not $force) -and (Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Length -gt 0)){ Write-Host ('[KEEP] '+$path); continue };" ^
  "  $bytes=New-Object byte[] 32; $rng.GetBytes($bytes);" ^
  "  $secret=-join ($bytes | ForEach-Object { $_.ToString('x2') });" ^
  "  [IO.File]::WriteAllText($path,$secret+[Environment]::NewLine,$utf8);" ^
  "  try { $acl=Get-Acl -LiteralPath $path; $acl.SetAccessRuleProtection($true,$false); $identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name; $rule=New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','Allow'); $acl.SetAccessRule($rule); Set-Acl -LiteralPath $path -AclObject $acl } catch { Write-Warning ('Could not restrict ACL for '+$path+': '+$_.Exception.Message) };" ^
  "  Write-Host ('[NEW]  '+$path);" ^
  "};" ^
  "$rng.Dispose();"

if errorlevel 1 (
  echo.
  echo Key generation failed. Check the PowerShell error above.
  exit /b 1
)

echo.
echo Generated without OpenSSL. Secret values were not printed.
echo Copy core-token and admin-token to each plugin's secrets folder:
echo   Velocity: plugins\cloudislands\secrets\
echo   Paper:    plugins\CloudIslands\secrets\
echo Point Core to the same files with CI_CORE_TOKEN_FILE and CI_ADMIN_TOKEN_FILE.
echo Use forwarding.secret as Velocity's forwarding-secret-file and set the same value in Paper's config\paper-global.yml.
echo Existing non-empty files are preserved. To replace them intentionally, run:
echo   %~nx0 "%CI_KEYS_OUTPUT%" --force

endlocal

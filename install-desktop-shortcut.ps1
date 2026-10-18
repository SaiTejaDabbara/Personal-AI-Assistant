$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not (Test-Path (Join-Path $root '.dist\Buddy.jar'))) {
    powershell -ExecutionPolicy Bypass -File (Join-Path $root 'build.ps1')
}

$javaExe = $null
$javaHome = $env:JAVA_HOME
if ($javaHome) {
    $candidate = Join-Path $javaHome 'bin\javaw.exe'
    if (Test-Path $candidate) { $javaExe = $candidate }
}

if (-not $javaExe) {
    $javaExeCandidates = @(
        'C:\Program Files\Java\jdk-17.0.20.1\bin\javaw.exe',
        'C:\Program Files\Java\jdk-17\bin\javaw.exe',
        'C:\Users\dabba\.jdks\corretto-17.0.14\bin\javaw.exe',
        'C:\Users\dabba\.jdks\corretto-18.0.2\bin\javaw.exe'
    )

    foreach ($candidate in $javaExeCandidates) {
        if (Test-Path $candidate) {
            $javaExe = $candidate
            break
        }
    }
}

if (-not $javaExe) {
    $javaCmd = Get-Command javaw -ErrorAction SilentlyContinue
    if ($javaCmd) { $javaExe = $javaCmd.Source }
}

if (-not $javaExe) {
    throw 'Could not find javaw.exe. Install Java and retry.'
}

$jarPath = (Resolve-Path (Join-Path $root '.dist\Buddy.jar')).Path
$desktop = [Environment]::GetFolderPath('Desktop')
$linkPath = Join-Path $desktop 'Buddy.lnk'

$ws = New-Object -ComObject WScript.Shell
$link = $ws.CreateShortcut($linkPath)
$link.TargetPath = $javaExe
$link.Arguments = '-jar "' + $jarPath + '" --autostart'
$link.WorkingDirectory = $root
$link.IconLocation = $javaExe
$link.Save()

Write-Host "Desktop shortcut created: $linkPath"

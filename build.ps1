$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$javaBin = $null

if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin'
    if ((Test-Path (Join-Path $candidate 'javac.exe')) -and (Test-Path (Join-Path $candidate 'jar.exe'))) {
        $javaBin = $candidate
    }
}

if (-not $javaBin) {
    $candidatePaths = @(
        'C:\Program Files\Java\jdk-17.0.20.1\bin',
        'C:\Program Files\Java\jdk-17\bin',
        'C:\Users\dabba\.jdks\corretto-17.0.14\bin',
        'C:\Users\dabba\.jdks\corretto-18.0.2\bin'
    )

    foreach ($candidate in $candidatePaths) {
        if ((Test-Path (Join-Path $candidate 'javac.exe')) -and (Test-Path (Join-Path $candidate 'jar.exe'))) {
            $javaBin = $candidate
            break
        }
    }
}

if (-not $javaBin) {
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) {
        $javaBin = Split-Path -Parent $javac.Source
    }
}

if (-not $javaBin -or -not (Test-Path (Join-Path $javaBin 'javac.exe')) -or -not (Test-Path (Join-Path $javaBin 'jar.exe'))) {
    throw 'Could not find a valid JDK installation with javac.exe and jar.exe on this machine.'
}

$outDir = Join-Path $root 'out'
$distDir = Join-Path $root '.dist'
$jarPath = Join-Path $distDir 'Buddy.jar'

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

$javaFiles = Get-ChildItem -Path $root -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if (-not $javaFiles) {
    throw 'No Java source files were found in the project.'
}

& (Join-Path $javaBin 'javac.exe') -d $outDir $javaFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (Test-Path $jarPath) { Remove-Item $jarPath -Force }

& (Join-Path $javaBin 'jar.exe') cfe $jarPath buddy.Main -C $outDir .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Build complete: $jarPath"

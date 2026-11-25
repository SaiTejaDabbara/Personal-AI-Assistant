$ErrorActionPreference = 'Stop'

$modelsRoot = if ($env:OLLAMA_MODELS) {
    $env:OLLAMA_MODELS
} else {
    Join-Path $env:USERPROFILE '.ollama\models'
}

$packagesRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
$serverPath = $null

if (Test-Path $packagesRoot) {
    $packageDirectories = Get-ChildItem -Path $packagesRoot -Directory -Filter 'ggml.llamacpp_*'
    foreach ($packageDirectory in ($packageDirectories | Sort-Object LastWriteTime -Descending)) {
        $candidate = Join-Path $packageDirectory.FullName 'llama-server.exe'
        if (Test-Path $candidate) {
            $serverPath = $candidate
            break
        }
    }
}

if (-not $serverPath) {
    $serverCommand = Get-Command llama-server.exe -ErrorAction SilentlyContinue
    if ($serverCommand) {
        $serverPath = $serverCommand.Source
    }
}

if (-not $serverPath) {
    throw 'The llama.cpp Vulkan runtime is missing. Install it with: winget install --id ggml.llamacpp'
}

$deviceOutput = (& $serverPath --list-devices 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not enumerate Vulkan devices: $deviceOutput"
}
if ($deviceOutput -notmatch '(?im)^\s*Vulkan0:\s+AMD Radeon') {
    throw "The AMD Vulkan GPU was not detected. Buddy was not started. Devices found: $deviceOutput"
}

function Get-ModelBlobPath {
    param(
        [string]$ModelName,
        [string]$Tag
    )

    $manifestPath = Join-Path $modelsRoot "manifests\registry.ollama.ai\library\$ModelName\$Tag"
    if (-not (Test-Path $manifestPath)) {
        throw "Ollama model manifest not found: $manifestPath"
    }

    $manifest = Get-Content -Path $manifestPath -Raw | ConvertFrom-Json
    $modelLayer = $manifest.layers |
        Where-Object { $_.mediaType -eq 'application/vnd.ollama.image.model' } |
        Select-Object -First 1
    if (-not $modelLayer) {
        throw "No GGUF model layer was found in manifest: $manifestPath"
    }

    $blobName = $modelLayer.digest -replace '^sha256:', 'sha256-'
    $modelPath = Join-Path $modelsRoot "blobs\$blobName"
    if (-not (Test-Path $modelPath)) {
        throw "Ollama model blob not found: $modelPath"
    }

    return $modelPath
}

function Wait-ForGpuServer {
    param(
        [int]$Port,
        [int]$ProcessId,
        [string]$ErrorLog
    )

    $lastError = 'The server did not become healthy.'
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            $details = if (Test-Path $ErrorLog) {
                (Get-Content -Path $ErrorLog -Tail 30) -join [Environment]::NewLine
            } else {
                'No server error log was created.'
            }
            throw "GPU server on port $Port exited during startup. $details"
        }

        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
            if ($health.status -eq 'ok') {
                Write-Host "Vulkan model server is ready on port $Port."
                return
            }
            $lastError = "Unexpected health response: $($health | ConvertTo-Json -Compress)"
        } catch {
            $lastError = $_.Exception.Message
        }

        Start-Sleep -Seconds 1
    }

    $details = if (Test-Path $ErrorLog) {
        (Get-Content -Path $ErrorLog -Tail 30) -join [Environment]::NewLine
    } else {
        'No server error log was created.'
    }
    throw "GPU server on port $Port did not become ready: $lastError $details"
}

function Ensure-GpuServer {
    param(
        [int]$Port,
        [string]$ModelPath,
        [string]$LogName
    )

    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)"
        $commandLine = $existing.CommandLine
        $hasExpectedExecutable = $existing.ExecutablePath -eq $serverPath
        $hasVulkanDevice = $commandLine -match '(?i)--device\s+Vulkan0'
        $hasFullOffload = $commandLine -match '(?i)--n-gpu-layers\s+999'
        $hasExpectedModel = $commandLine -like "*$ModelPath*"
        if (
            -not $hasExpectedExecutable -or
            -not $hasVulkanDevice -or
            -not $hasFullOffload -or
            -not $hasExpectedModel
        ) {
            throw "Port $Port is already used by a server that is not pinned to this Vulkan model. Buddy was not started."
        }

        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 3
            if ($health.status -eq 'ok') {
                Write-Host "Reusing the Vulkan model server on port $Port."
                return
            }
        } catch {
            throw "The GPU server on port $Port is present but unhealthy: $($_.Exception.Message)"
        }
        throw "The GPU server on port $Port is present but unhealthy."
    }

    $logDirectory = Join-Path $env:LOCALAPPDATA 'Buddy\logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $standardOutput = Join-Path $logDirectory "$LogName.out.log"
    $standardError = Join-Path $logDirectory "$LogName.err.log"
    $arguments = "--model `"$ModelPath`" --host 127.0.0.1 --port $Port --device Vulkan0 --n-gpu-layers 999 --ctx-size 1024 --parallel 1"

    $process = Start-Process `
        -FilePath $serverPath `
        -ArgumentList $arguments `
        -WorkingDirectory (Split-Path -Parent $serverPath) `
        -RedirectStandardOutput $standardOutput `
        -RedirectStandardError $standardError `
        -PassThru

    Wait-ForGpuServer -Port $Port -ProcessId $process.Id -ErrorLog $standardError
}

$chatModelPath = Get-ModelBlobPath -ModelName 'llama3.2' -Tag 'latest'
$plannerModelPath = Get-ModelBlobPath -ModelName 'qwen2.5' -Tag '0.5b'

Ensure-GpuServer -Port 11436 -ModelPath $chatModelPath -LogName 'buddy-llama3.2-vulkan'
Ensure-GpuServer -Port 11437 -ModelPath $plannerModelPath -LogName 'buddy-qwen2.5-0.5b-vulkan'

Write-Host 'Buddy GPU inference is ready. Both models are pinned to Vulkan0.'

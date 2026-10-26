# Starts UptimeWatch and a free Cloudflare quick tunnel, then prints the public link.
# Usage (from this folder):  powershell -ExecutionPolicy Bypass -File .\start-public.ps1
# The link changes every time and works only while this window stays open. Ctrl+C stops both.

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$cloudflared = Join-Path $here "..\tools\cloudflared\cloudflared.exe"
$jar = Join-Path $here "target\uptimewatch-0.1.0.jar"
$envFile = Join-Path $here ".env.local"
$tunnelLog = Join-Path $here "tunnel.log"
$appLog = Join-Path $here "app.log"

if (-not (Test-Path $cloudflared)) { throw "cloudflared.exe not found at $cloudflared" }
if (-not (Test-Path $jar)) { Write-Host "Building the app..."; Push-Location $here; mvn -q -B package -DskipTests; Pop-Location }

# Never go public with the default dev key: create a random admin key on first run.
if (-not (Test-Path $envFile)) {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $key = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    [IO.File]::WriteAllText($envFile, "UPTIMEWATCH_API_KEY=$key`n")
    Write-Host "Created a new admin key in .env.local (open that file to see it)."
}
Get-Content $envFile | Where-Object { $_ -match '^\s*([A-Z_]+)=(.*)$' } | ForEach-Object {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], "Process")
}

# A second copy can't bind the port, and the tunnel would silently serve the old one.
$busy = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    $owner = Get-Process -Id $busy[0].OwningProcess -ErrorAction SilentlyContinue
    throw "Port 8080 is already used by '$($owner.ProcessName)' (PID $($busy[0].OwningProcess)). Close it first, e.g.: Stop-Process -Id $($busy[0].OwningProcess)"
}

$app = $null
$tunnel = $null
try {
    $app = Start-Process java -ArgumentList "-jar", "`"$jar`"" -WorkingDirectory $here -PassThru `
        -WindowStyle Hidden -RedirectStandardOutput $appLog -RedirectStandardError "$appLog.err"
    Write-Host "Starting UptimeWatch..."
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $app.HasExited; $i++) {
        try { Invoke-WebRequest "http://localhost:8080/api/monitors" -UseBasicParsing -TimeoutSec 2 | Out-Null; $ready = $true; break }
        catch { Start-Sleep 2 }
    }
    if (-not $ready) { throw "UptimeWatch did not start. See app.log and app.log.err in this folder." }
    Write-Host "UptimeWatch is running on http://localhost:8080"

    Remove-Item $tunnelLog -ErrorAction SilentlyContinue
    $tunnel = Start-Process $cloudflared -ArgumentList "tunnel", "--no-autoupdate", "--url", "http://localhost:8080" `
        -PassThru -WindowStyle Hidden -RedirectStandardError $tunnelLog
    Write-Host "Opening the Cloudflare tunnel..."
    $url = $null
    for ($i = 0; $i -lt 45 -and -not $tunnel.HasExited; $i++) {
        Start-Sleep 2
        $log = Get-Content $tunnelLog -Raw -ErrorAction SilentlyContinue
        if ($log -match 'https://[a-z0-9-]+\.trycloudflare\.com' -and $log -match 'Registered tunnel connection') {
            $url = $Matches[0]; break
        }
    }
    if (-not $url) { throw "The tunnel did not start. See tunnel.log in this folder." }
    $url = ([regex]::Match((Get-Content $tunnelLog -Raw), 'https://[a-z0-9-]+\.trycloudflare\.com')).Value

    Write-Host ""
    Write-Host "  ==============================================================" -ForegroundColor Green
    Write-Host "   UptimeWatch is LIVE:  $url" -ForegroundColor Green
    Write-Host "  ==============================================================" -ForegroundColor Green
    Write-Host "   Keep this window open. Press Ctrl+C to stop sharing."
    Write-Host ""
    Wait-Process -Id $tunnel.Id
} finally {
    foreach ($p in @($tunnel, $app)) {
        if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    }
    Write-Host "Stopped UptimeWatch and the tunnel."
}

# Resets the local Arcade stack and seeds the demo users alice and bob.
#
# WARNING: this DELETES the local database volume (pgdata) and all data in it.
# It only affects this Compose project. Run it from anywhere; it changes to the repository root.
# Windows PowerShell 5.1 compatible.
#
# Usage:    .\scripts\dev-reset.ps1
# Optional: $env:BACKEND_URL = 'http://localhost:8080' (default), when the backend is reached elsewhere.

$ErrorActionPreference = 'Stop'

function Get-HttpStatusCode([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

Set-Location (Split-Path -Parent $PSScriptRoot)

$backendUrl = 'http://localhost:8080'
if ($env:BACKEND_URL) {
    $backendUrl = $env:BACKEND_URL
}
$waitSeconds = 120
$devPassword = 'arcade-dev-password'

if (-not (Test-Path '.env')) {
    throw 'Missing .env. Create it first: Copy-Item .env.example .env'
}

Write-Host 'Deleting the local database volume and starting a fresh, seeded stack...'
docker compose down -v
if ($LASTEXITCODE -ne 0) { throw 'docker compose down -v failed.' }

$env:ARCADE_SEED_ENABLED = 'true'
try {
    docker compose up --build -V -d
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed.' }
} finally {
    Remove-Item Env:ARCADE_SEED_ENABLED -ErrorAction SilentlyContinue
}

Write-Host "Waiting for the backend at $backendUrl (up to $waitSeconds seconds)..."
$deadline = (Get-Date).AddSeconds($waitSeconds)
# 401 means the backend answers and requires a token, which is what we want here.
while ((Get-HttpStatusCode "$backendUrl/api/rooms") -ne 401) {
    if ((Get-Date) -gt $deadline) {
        throw "The backend did not answer within $waitSeconds seconds. Check: docker compose logs backend"
    }
    Start-Sleep -Seconds 2
}

Write-Host ''
Write-Host "Demo accounts (password: $devPassword):"
Write-Host '  alice  log in in a normal browser window'
Write-Host '  bob    log in in a private/incognito window'

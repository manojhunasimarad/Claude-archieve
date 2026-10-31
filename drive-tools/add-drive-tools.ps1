# Copies the Google Drive cleanup scripts and the private code-assistant setup from this laptop
# into this repo, then commits and pushes them.
#
# Run from anywhere (Windows PowerShell):
#   powershell -ExecutionPolicy Bypass -File <path-to-Claude-archieve>\drive-tools\add-drive-tools.ps1
#
# Only scripts and config files are copied. catalog.db, plan.csv, events.csv, moves_log.csv,
# verify_report.csv, logs and spreadsheets stay on the laptop: they list every personal file path.

$ErrorActionPreference = 'Stop'
$src  = 'C:\Users\manoj\Documents\buffer'
$here = $PSScriptRoot
$repo = Split-Path -Parent $here
$dst  = Join-Path $here 'scripts'

if (-not (Test-Path $src)) { throw "Folder not found: $src. Edit `$src at the top of this script." }
New-Item -ItemType Directory -Force -Path $dst | Out-Null

$copied = @()
foreach ($f in Get-ChildItem -Path (Join-Path $src '*') -File -Include *.py, *.ps1) {
    Copy-Item $f.FullName -Destination $dst -Force
    $copied += "scripts\$($f.Name)"
}

$ca = Join-Path $src 'code-assistant'
if (Test-Path $ca) {
    $caDst = Join-Path $here 'code-assistant'
    New-Item -ItemType Directory -Force -Path $caDst | Out-Null
    foreach ($name in 'docker-compose.yml', 'setup.ps1', 'continue-config.yaml', 'README.txt') {
        $p = Join-Path $ca $name
        if (Test-Path $p) { Copy-Item $p -Destination $caDst -Force; $copied += "code-assistant\$name" }
    }
}

if ($copied.Count -eq 0) { throw "No scripts found in $src." }
Write-Host "Copied $($copied.Count) files:" -ForegroundColor Green
$copied | ForEach-Object { Write-Host "  $_" }

git -C $repo pull --ff-only
git -C $repo add drive-tools
git -C $repo commit -m "Add Drive cleanup scripts and local code-assistant setup"
git -C $repo push
Write-Host "Pushed. Check https://github.com/manojhunasimarad/Claude-archieve/tree/main/drive-tools" -ForegroundColor Green

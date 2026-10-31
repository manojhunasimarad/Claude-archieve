<#
  Private AI code assistant: the model runs on the V100, your code and its search index stay on this laptop.
  Run in PowerShell from this folder:

    powershell -ExecutionPolicy Bypass -File v100.ps1 setup      # once: copy the scripts to the V100 and install Ollama there
    powershell -ExecutionPolicy Bypass -File v100.ps1 continue   # once: add "V100 coder" to Continue in VS Code
    powershell -ExecutionPolicy Bypass -File v100.ps1 connect    # each session: start it and open the tunnel; keep the window open
    powershell -ExecutionPolicy Bypass -File v100.ps1 status     # is it running, is the tunnel open, how busy is the GPU
    powershell -ExecutionPolicy Bypass -File v100.ps1 stop       # unload it and free the GPU for others

  Needs: Sophos Connect VPN connected, and the "v100" host in your SSH config (the one VS Code Remote-SSH uses).
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('setup', 'connect', 'continue', 'status', 'stop')]
    [string]$Command = 'connect',
    [string]$HostAlias = 'v100',
    [int]$LocalPort = 11435
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$remoteDir = '~/ollama-v100'

function Assert-Ssh {
    if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
        throw 'ssh was not found. Windows Settings > System > Optional features > add "OpenSSH Client".'
    }
}

function ConvertFrom-EnvLines($lines) {
    $map = @{}
    foreach ($line in $lines) {
        if ($line -match '^([A-Z_]+)=(.*)$') { $map[$Matches[1]] = $Matches[2].Trim() }
    }
    return $map
}

switch ($Command) {
    'setup' {
        Assert-Ssh
        $src = Join-Path $here '..\v100'
        Write-Host "Copying the V100 scripts to ${HostAlias}:$remoteDir ..." -ForegroundColor Cyan
        ssh $HostAlias "mkdir -p $remoteDir"
        scp (Join-Path $src 'setup.sh') (Join-Path $src 'ollama-ctl.sh') "${HostAlias}:ollama-v100/"
        if ($LASTEXITCODE -ne 0) { throw 'Copy failed. Is the VPN on, and does "ssh v100" work?' }
        # Strip Windows line endings in case Git converted them, then run the installer on the V100.
        ssh -t $HostAlias "sed -i 's/\r$//' $remoteDir/*.sh && chmod +x $remoteDir/*.sh && bash $remoteDir/setup.sh"
    }

    'connect' {
        Assert-Ssh
        Write-Host "Starting Ollama on $HostAlias if it is not running ..." -ForegroundColor Cyan
        $lines = ssh $HostAlias "bash $remoteDir/ollama-ctl.sh start 1>&2 && cat ~/.config/ollama-v100.env"
        if ($LASTEXITCODE -ne 0) { throw "Could not start Ollama on $HostAlias. Try: v100.ps1 status" }
        $cfg = ConvertFrom-EnvLines $lines
        $remotePort = ($cfg['OLLAMA_HOST'] -split ':')[1]
        Write-Host ''
        Write-Host "Tunnel: this laptop 127.0.0.1:$LocalPort -> $HostAlias 127.0.0.1:$remotePort, model $($cfg['MODEL'])" -ForegroundColor Green
        Write-Host "In VS Code, pick 'V100 coder' in the Continue model list. Keep this window open; Ctrl+C closes the tunnel." -ForegroundColor Green
        ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L "${LocalPort}:127.0.0.1:${remotePort}" $HostAlias
    }

    'continue' {
        $cfgPath = Join-Path $HOME '.continue\config.yaml'
        $model = 'qwen2.5-coder:14b'
        $ctx = 16384
        try {
            Assert-Ssh
            $remote = ConvertFrom-EnvLines (ssh $HostAlias 'cat ~/.config/ollama-v100.env')
            if ($remote['MODEL']) { $model = $remote['MODEL']; $ctx = [int]$remote['OLLAMA_CONTEXT_LENGTH'] }
        } catch {
            Write-Host "Could not read the V100 settings, so using $model. Edit $cfgPath later if needed." -ForegroundColor Yellow
        }
        $block = @(
            '- name: V100 coder',
            '  provider: ollama',
            "  model: $model",
            "  apiBase: http://127.0.0.1:$LocalPort",
            '  roles: [chat, edit, apply]',
            '  capabilities: [tool_use]',
            '  defaultCompletionOptions:',
            "    contextLength: $ctx"
        )
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        $out = New-Object System.Collections.Generic.List[string]
        if (Test-Path $cfgPath) {
            $lines = [System.IO.File]::ReadAllLines($cfgPath)
            if ($lines | Where-Object { $_ -match 'name:\s*V100 coder' }) {
                Write-Host "'V100 coder' is already in $cfgPath." -ForegroundColor Green
                return
            }
            Copy-Item $cfgPath "$cfgPath.bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
            $inserted = $false
            for ($i = 0; $i -lt $lines.Count; $i++) {
                $out.Add($lines[$i])
                if (-not $inserted -and $lines[$i] -match '^models:\s*$') {
                    # Match the indentation of the existing list items.
                    $indent = '  '
                    for ($j = $i + 1; $j -lt $lines.Count; $j++) {
                        if ($lines[$j].Trim() -eq '') { continue }
                        if ($lines[$j] -match '^(\s*)-\s') { $indent = $Matches[1] }
                        break
                    }
                    foreach ($b in $block) { $out.Add($indent + $b) }
                    $inserted = $true
                }
            }
            if (-not $inserted) {
                $out.Add('models:')
                foreach ($b in $block) { $out.Add('  ' + $b) }
            }
        } else {
            New-Item -ItemType Directory -Force -Path (Split-Path $cfgPath) | Out-Null
            foreach ($l in @('name: Private assistant', 'version: 1.0.0', 'schema: v1', 'models:')) { $out.Add($l) }
            foreach ($b in $block) { $out.Add('  ' + $b) }
        }
        [System.IO.File]::WriteAllLines($cfgPath, $out, $utf8)
        Write-Host "Added 'V100 coder' to $cfgPath (a backup of the old file is next to it)." -ForegroundColor Green
        Write-Host 'Codebase indexing and autocomplete stay on the laptop model; only chat goes to the V100.' -ForegroundColor Green
    }

    'status' {
        Assert-Ssh
        ssh $HostAlias "bash $remoteDir/ollama-ctl.sh status; nvidia-smi --query-gpu=index,name,memory.used,memory.total,utilization.gpu --format=csv"
        try {
            $v = Invoke-RestMethod -TimeoutSec 3 -Uri "http://127.0.0.1:$LocalPort/api/version"
            Write-Host "Tunnel is open (Ollama $($v.version))." -ForegroundColor Green
        } catch {
            Write-Host 'Tunnel is not open. Run: v100.ps1 connect' -ForegroundColor Yellow
        }
    }

    'stop' {
        Assert-Ssh
        ssh $HostAlias "bash $remoteDir/ollama-ctl.sh stop"
    }
}

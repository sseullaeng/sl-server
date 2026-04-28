# Codex 리뷰 실시간 모니터 (Windows PowerShell 7+)
# 사용     : powershell -File ./scripts/codex-watch.ps1
# raw 모드 : powershell -File ./scripts/codex-watch.ps1 -Raw
# 종료     : Ctrl+C
param([switch]$Raw)

$ErrorActionPreference = 'Stop'

$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$LogFile     = Join-Path $ProjectRoot '.logs/codex-review.log'

if (-not (Test-Path $LogFile)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $LogFile) | Out-Null
    New-Item -ItemType File -Path $LogFile | Out-Null
}

$RawMode = $Raw.IsPresent -or ($env:CODEX_WATCH_RAW -eq '1')

$Sep = '════════════════════════════════════════════════════════════════'
$Sub = '────────────────────────────────────────────────────────────────'
$ModeTag = if ($RawMode) { '[raw]' } else { '[pretty]' }

Write-Host $Sep -ForegroundColor White
Write-Host (' Codex 리뷰 실시간 모니터  ' + $ModeTag) -ForegroundColor White
Write-Host (' 시작 시각 : ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host (' 로그 경로 : ' + $LogFile)
Write-Host  ' 종료      : Ctrl+C'
Write-Host $Sep -ForegroundColor White

function Write-ColoredBody([string]$Text) {
    foreach ($line in ($Text -split "`n")) {
        $color = $null
        switch -Wildcard ($line) {
            { $_ -match 'Critical|CRITICAL|🔴|error|ERROR|Error' } { $color = 'Red';    break }
            { $_ -match 'Warning|WARNING|🟡|warn|WARN' }           { $color = 'Yellow'; break }
            { $_ -match 'Suggestion|SUGGEST|🟢|hint|HINT' }        { $color = 'Cyan';   break }
            { $_ -match '✅|good|GOOD|passed|PASS' }                { $color = 'Green';  break }
        }
        if ($color) { Write-Host ('  ' + $line) -ForegroundColor $color }
        else        { Write-Host ('  ' + $line) }
    }
}

if ($RawMode) {
    Get-Content -Wait -Tail 0 -Path $LogFile | ForEach-Object {
        $line = $_
        switch -Wildcard ($line) {
            { $_ -match 'Critical|🔴|error|ERROR|Error' } { Write-Host $line -ForegroundColor Red;    break }
            { $_ -match 'Warning|🟡|warn|WARN' }          { Write-Host $line -ForegroundColor Yellow; break }
            { $_ -match 'Suggestion|🟢|hint|HINT' }       { Write-Host $line -ForegroundColor Cyan;   break }
            { $_ -match '✅|good|passed|PASS' }            { Write-Host $line -ForegroundColor Green;  break }
            default                                        { Write-Host $line }
        }
    }
    return
}

# pretty mode
Get-Content -Wait -Tail 0 -Path $LogFile | ForEach-Object {
    try { $j = $_ | ConvertFrom-Json -ErrorAction Stop } catch { return }

    if ($j.method -eq 'tools/call' -and $j.params.name -eq 'codex') {
        Write-Host ''
        Write-Host '📤 REQUEST' -ForegroundColor Cyan -NoNewline
        Write-Host (' (' + (Get-Date -Format 'HH:mm:ss') + ')') -ForegroundColor DarkGray
        Write-Host $Sub -ForegroundColor DarkGray
        foreach ($line in (($j.params.arguments.prompt) -split "`n")) {
            Write-Host ('  ' + $line)
        }
    }
    elseif ($j.method -eq 'codex/event') {
        $m = $j.params.msg
        if     ($m.type -eq 'task_started')  { Write-Host ('🤔 thinking... (turn ' + $m.turn_id + ')') -ForegroundColor DarkGray }
        elseif ($m.type -eq 'task_complete') {
            Write-Host ''
            Write-Host '📥 RESPONSE' -ForegroundColor Green -NoNewline
            Write-Host (' (' + $m.duration_ms + 'ms, ttft ' + $m.time_to_first_token_ms + 'ms)') -ForegroundColor DarkGray
            Write-Host $Sub -ForegroundColor DarkGray
            Write-ColoredBody $m.last_agent_message
            Write-Host $Sep -ForegroundColor DarkGray
            Write-Host ''
        }
    }
}

param(
  [Parameter(Mandatory=$true)][string]$RunId,
  [Parameter(Mandatory=$true)][string]$PromptFile,
  [Parameter(Mandatory=$true)][string]$Model,
  [Parameter(Mandatory=$true)][string]$Reasoning,
  [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$OutputRoot,
  [string]$Profile = '',
  [ValidateSet('read-only','workspace-write','danger-full-access')][string]$Sandbox = 'workspace-write',
  [ValidateSet('untrusted','on-request','never')][string]$ApprovalPolicy = 'never',
  [ValidateRange(1,86400)][int]$TimeoutSeconds = 1800,
  [switch]$IgnoreUserConfig
)

$ErrorActionPreference = 'Stop'
$PromptPath = Resolve-Path -LiteralPath $PromptFile -ErrorAction Stop
if (-not (Test-Path -LiteralPath $PromptPath -PathType Leaf)) {
  throw "Prompt file not found: $PromptFile"
}
$Prompt = Get-Content -LiteralPath $PromptPath -Raw
if ([string]::IsNullOrWhiteSpace($Prompt)) {
  throw "Prompt file is empty: $PromptFile"
}

$RunnerRoot = $PSScriptRoot
$RunDir = Join-Path $OutputRoot $RunId
if (Test-Path -LiteralPath $RunDir) { throw "Run directory already exists: $RunDir" }
New-Item -ItemType Directory -Path $RunDir | Out-Null

$CaptureArgs = @(
  (Join-Path $RunnerRoot 'capture_environment.py'),
  '--output', (Join-Path $RunDir 'environment.json'),
  '--model', $Model,
  '--reasoning', $Reasoning,
  '--profile', $Profile,
  '--approval-policy', $ApprovalPolicy,
  '--sandbox', $Sandbox
)
if ($IgnoreUserConfig) { $CaptureArgs += '--ignore-user-config' }
& python @CaptureArgs
if ($LASTEXITCODE -ne 0) { throw "Failed to capture experiment environment." }

Copy-Item -LiteralPath $PromptPath -Destination (Join-Path $RunDir 'prompt.md')
$Started = Get-Date
$ExecArgs = @(
  (Join-Path $RunnerRoot 'run_codex_exec.py'),
  '--prompt', $PromptPath,
  '--events', (Join-Path $RunDir 'events.jsonl'),
  '--stderr', (Join-Path $RunDir 'stderr.log'),
  '--model', $Model,
  '--reasoning', $Reasoning,
  '--sandbox', $Sandbox,
  '--approval-policy', $ApprovalPolicy,
  '--timeout-seconds', $TimeoutSeconds
)
if ($Profile) { $ExecArgs += @('--profile', $Profile) }
if ($IgnoreUserConfig) { $ExecArgs += '--ignore-user-config' }
& python @ExecArgs
$ExitCode = $LASTEXITCODE
$Finished = Get-Date

@{
  run_id = $RunId
  started_at = $Started.ToString('o')
  finished_at = $Finished.ToString('o')
  wall_seconds = [math]::Round(($Finished - $Started).TotalSeconds, 3)
  codex_exit_code = $ExitCode
  sandbox = $Sandbox
  model = $Model
  reasoning_effort = $Reasoning
  profile = $(if ($Profile) { $Profile } else { $null })
  approval_policy = $ApprovalPolicy
  timeout_seconds = $TimeoutSeconds
  ignore_user_config = [bool]$IgnoreUserConfig
} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $RunDir 'run.json')

$ParseScript = Join-Path $RunnerRoot 'parse_codex_jsonl.py'
& python $ParseScript `
  (Join-Path $RunDir 'events.jsonl') `
  --metadata (Join-Path $RunDir 'run.json') `
  --output (Join-Path $RunDir 'summary.json')
Write-Host "Run saved to $RunDir"
exit $ExitCode

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Build = Join-Path $Root "build\classes"
Remove-Item -Recurse -Force $Build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Build | Out-Null
$SourceRoots = @((Join-Path $Root "src\main\java"), (Join-Path $Root "src\test\java"))
$Sources = Get-ChildItem -Path $SourceRoots -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $Sources) { throw "No Java sources found" }
& javac --release 17 -encoding UTF-8 -d $Build $Sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $Build lab.benchmark.AllPublicTests
exit $LASTEXITCODE

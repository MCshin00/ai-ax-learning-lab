$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Out = Join-Path $Root "out"
Remove-Item -Recurse -Force $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Out | Out-Null
javac --release 17 -d $Out `
  (Join-Path $Root "src\TicketTitleNormalizer.java") `
  (Join-Path $Root "test\TicketTitleNormalizerTest.java")
java -cp $Out lab.week01.TicketTitleNormalizerTest

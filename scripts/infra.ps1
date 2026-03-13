$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
$composeArgs = @("-f", "docker-compose.local.yml")

if ($args.Count -eq 0) {
    $composeArgs += @("up", "-d")
} else {
    $composeArgs += $args
}

Push-Location $repoRoot
try {
    & docker compose @composeArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
$composeArgs = @("-f", "docker-compose.local.yml")

# Default to `up -d` when no args are given. Mirror of scripts/infra.sh —
# see that file for the full rationale on the auto-build and volume guard
# below. The two scripts must stay behavior-equivalent; if you change one,
# update the other in the same commit.
if ($args.Count -eq 0) {
    $userArgs = @("up", "-d")
} else {
    $userArgs = @($args)
}

# Auto-inject --build on any `up` invocation unless --build / --no-build
# is already present. Prevents a cached wealthpay-postgres image from
# being reused after a Dockerfile bump.
if ($userArgs[0] -eq "up" -and -not ($userArgs -contains "--build") -and -not ($userArgs -contains "--no-build")) {
    $userArgs += "--build"
}

# Pre-flight: refuse to start when an existing pg_data volume's on-disk
# layout doesn't match what the current docker-compose mount expects.
# See scripts/infra.sh for the comprehensive comment on why this guard
# exists and how it should be maintained across future major-version
# cutovers.
if ($userArgs[0] -eq "up") {
    $pgDataVolume = if ($env:COMPOSE_PROJECT_NAME) {
        "$($env:COMPOSE_PROJECT_NAME)_pg_data"
    } else {
        "wealthpay_pg_data"
    }

    docker volume inspect $pgDataVolume *>$null
    if ($LASTEXITCODE -eq 0) {
        docker run --rm -v "${pgDataVolume}:/check" alpine:3 sh -c "test -f /check/PG_VERSION" *>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "ERROR: '$pgDataVolume' has a pre-PG18 layout (PG_VERSION at volume root)." -ForegroundColor Red
            Write-Host "       The current stack mounts pg_data at /var/lib/postgresql, expecting"
            Write-Host "       PGDATA under ./data/. Starting against the old layout risks"
            Write-Host "       abandoning the prior cluster files or initdb failure."
            Write-Host ""
            Write-Host "       This stack is PG18-only; no in-place migration from a local dev volume."
            Write-Host "       To preserve data: take a logical dump from the running pre-PG18 stack"
            Write-Host "         before discarding the volume, then reload it after bring-up."
            Write-Host "       To discard and start fresh (DESTROYS DATA):"
            Write-Host "         docker volume rm $pgDataVolume; .\scripts\infra.ps1 up -d --build"
            exit 1
        }
    }
}

$composeArgs += $userArgs

Push-Location $repoRoot
try {
    & docker compose @composeArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

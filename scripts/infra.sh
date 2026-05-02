#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

compose_args=(-f docker-compose.local.yml)

case "$(uname -s)" in
  Linux)
    compose_args+=(-f docker-compose.local.linux.yml)
    ;;
esac

# Default to `up -d` when no args are given.
if [ "$#" -eq 0 ]; then
  set -- up -d
fi

# Auto-inject --build on ANY `up` invocation (no-arg or explicit) unless
# the caller has stated their intent with --build or --no-build. This
# guarantees the runbook's documented commands (e.g.
# `./scripts/infra.sh up -d`) cannot silently reuse a stale cached
# wealthpay-postgres image after a Dockerfile bump — a concrete failure
# mode for a major Postgres version cutover. --build is cheap when
# Docker's layer cache is hot; --no-build is the documented opt-out for
# callers who know their cache is fresh.
if [[ "${1:-}" == "up" ]]; then
  has_build_arg=0
  for arg in "$@"; do
    case "${arg}" in
      --build|--no-build) has_build_arg=1 ;;
    esac
  done
  if [[ "${has_build_arg}" -eq 0 ]]; then
    set -- "$@" --build
  fi
fi

# Pre-flight: refuse to start when an existing pg_data volume's on-disk
# layout doesn't match what the current docker-compose mount expects.
#
# Pattern (recurrent across major-version cutovers): pg_upgrade --link
# based migrations periodically move the PGDATA mount path within the
# pg_data volume so old and new clusters' data subdirectories can coexist
# during the upgrade. Running `up` against a volume whose layout predates
# the current mount path is dangerous — the new cluster's entrypoint sees
# an empty PGDATA, runs initdb, and either fails or overlays a fresh
# cluster on top, abandoning or corrupting the prior data depending on
# the PG point version's initdb behavior against a non-empty parent.
#
# Concrete check below catches an obsolete layout via a marker file that
# only exists on volumes left behind by a previous mount-path scheme.
# When a future major-version cutover moves the mount path again, update
# the marker(s) checked here and the runbook pointer in the error
# message. The PRINCIPLE — refuse layouts the current stack can't safely
# consume — is permanent; the specific markers and runbook link are not.
#
# Compose project-prefixes named volumes — on this stack `pg_data` in
# the compose file becomes `wealthpay_pg_data` on the Docker daemon.
# Override via COMPOSE_PROJECT_NAME if your setup differs.
PG_DATA_VOLUME="${COMPOSE_PROJECT_NAME:-wealthpay}_pg_data"
if [[ "${1:-}" == "up" ]] && docker volume inspect "${PG_DATA_VOLUME}" &>/dev/null; then
  if docker run --rm -v "${PG_DATA_VOLUME}":/check alpine:3 \
       sh -c 'test -f /check/PG_VERSION' &>/dev/null; then
    echo "ERROR: '${PG_DATA_VOLUME}' has a pre-PG18 layout (PG_VERSION at volume root)." >&2
    echo "       The current stack mounts pg_data at /var/lib/postgresql, expecting" >&2
    echo "       PGDATA under ./data/. Starting against the old layout risks" >&2
    echo "       abandoning the prior cluster files or initdb failure." >&2
    echo "" >&2
    echo "       To migrate existing data: see docs/postgres-18-migration-plan.md" >&2
    echo "       To start fresh (DESTROYS DATA):" >&2
    echo "         docker volume rm ${PG_DATA_VOLUME} && ./scripts/infra.sh up -d --build" >&2
    exit 1
  fi
fi

cd "${REPO_ROOT}"
exec docker compose "${compose_args[@]}" "$@"

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

if [ "$#" -eq 0 ]; then
  set -- up -d
fi

cd "${REPO_ROOT}"
exec docker compose "${compose_args[@]}" "$@"

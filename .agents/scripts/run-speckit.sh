#!/usr/bin/env bash
# Run the shared Spec Kit scripts without changing Claude's installation metadata.
set -euo pipefail

case "${1:-}" in
    check-prerequisites|setup-plan|setup-tasks|create-new-feature)
        script_name="$1"
        shift
        ;;
    --help|-h|"")
        printf '%s\n' 'Usage: bash .codex/scripts/run-speckit.sh <check-prerequisites|setup-plan|setup-tasks|create-new-feature> [args...]'
        exit 0
        ;;
    *)
        printf 'Unknown Spec Kit script: %s\n' "$1" >&2
        exit 2
        ;;
esac

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

# Windows can advertise python3.exe through App Execution Aliases even when it
# cannot run Python. Spec Kit checks command existence, so supply a working
# process-local function when only `python` is usable. Nothing is installed.
if python3 -c 'import json' >/dev/null 2>&1; then
    :
elif python -c 'import json' >/dev/null 2>&1; then
    python3() { python "$@"; }
    export -f python3
elif ! command -v jq >/dev/null 2>&1; then
    printf '%s\n' 'Spec Kit needs a working Python interpreter or jq.' >&2
    exit 1
fi

cd -- "$repo_root"
exec bash ".specify/scripts/bash/${script_name}.sh" "$@"

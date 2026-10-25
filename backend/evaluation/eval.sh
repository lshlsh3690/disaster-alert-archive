#!/usr/bin/env bash
# 사용법: bash eval.sh <DB 이름> <라벨 CSV 경로>
# 예:     bash eval.sh disaster_alert_eval_rp1 datasets/cluster-eval-2026-10-incident.csv
# 로컬 docker 의 postgres 컨테이너에서 읽기만 한다(운영 DB 아님).
set -e
DB="$1"; CSV="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
export MSYS_NO_PATHCONV=1
docker cp "$CSV" postgres:/tmp/labels.csv
docker exec -i postgres psql -U postgres -d "$DB" -q -v ON_ERROR_STOP=1 < "$HERE/eval_rates.sql"

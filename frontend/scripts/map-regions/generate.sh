#!/usr/bin/env bash
# 대시보드 지도의 시군구/읍면동 경계 JSON 을 다시 만든다. 사전 준비와 설명은 README.md 참고.
#
#   cd frontend && PSQL="docker exec -i tmp-postgis psql -U postgres -d gis" bash scripts/map-regions/generate.sh
#
# 결과(frontend/public/map/):
#   sigungu.json          시군구(코드 5자리) 경계 + 경계 상자(b)
#   emd/<시도2자리>.json   읍면동(코드 8자리) 경계, 시도별 파일
set -euo pipefail

PSQL="${PSQL:-docker exec -i tmp-postgis psql -U postgres -d gis}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/../../public/map}"

# 기존 koreaSido.data.ts(시도 SVG)와 겹치도록 맞춘 메르카토르 변환 — build.sql 머리말 참고.
#   화면 x = 98.0650*lon - 12195.995 , 화면 y = -98.1811*mercY_deg + 4138.969
# EPSG:3857(미터) 기준 아핀 계수로 환산한다: px/m = (px/deg) * 180 / (pi * R)
read -r SX SY < <(node -e 'const k=180/(Math.PI*6378137);console.log(98.0650*k,98.1811*k)')
OX=-12195.995
OY=4138.969

mkdir -p "$OUT/emd"

# 값이 NULL/비어 있어 export 가 빈 줄을 내면 minify 단계에서 실패하도록 한다(조용히 빈 파일을 만들지 않는다).
minify() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const j=JSON.parse(s);process.stdout.write(JSON.stringify(j))})'; }

echo "1) build.sql — 3857 -> 화면 좌표, 읍면동 -> 시군구 병합"
# grep -v 는 NOTICE 만 걸러내고 일치하는 줄이 없을 때의 종료코드 1 만 무시한다(중괄호 안). psql 이 실패하면 pipefail 로
# 스크립트가 멈춘다 — 실패한 build.sql 의 낡은 테이블로 JSON 을 만들지 않게 한다.
$PSQL -q -v ON_ERROR_STOP=1 -v sx="$SX" -v sy="$SY" -v ox="$OX" -v oy="$OY" < "$HERE/build.sql" 2>&1 | { grep -v NOTICE || true; }

echo "2) sigungu.json"
# 임시 파일에 쓰고 성공했을 때만 교체한다 — 중간에 실패해도 기존 파일이 빈 파일로 덮어써지지 않는다.
$PSQL -At -v ON_ERROR_STOP=1 -v which=sigungu -v tol=0.4 < "$HERE/export.sql" | minify > "$OUT/sigungu.json.tmp"
mv "$OUT/sigungu.json.tmp" "$OUT/sigungu.json"

echo "3) emd/<시도>.json"
# 명령 치환을 for 안에 두면 실패해도 set -e 가 멈추지 않으므로 먼저 변수에 받는다.
SIDOS="$($PSQL -At -v ON_ERROR_STOP=1 -c "SELECT DISTINCT left(code8,2) FROM map_emd ORDER BY 1")"
for sido in $SIDOS; do
  $PSQL -At -v ON_ERROR_STOP=1 -v which=emd -v sido="$sido" -v tol=0.06 < "$HERE/export.sql" | minify > "$OUT/emd/$sido.json.tmp"
  mv "$OUT/emd/$sido.json.tmp" "$OUT/emd/$sido.json"
done

ls -l "$OUT/sigungu.json" "$OUT/emd" | awk '{print $5, $9}'

#!/usr/bin/env bash
# 운영에서 내보낸 alert_kind CSV(disaster_alert_id, sn, alert_kind)를 평가 사본 disaster_alert_eval_v5 에 덮어쓴다.
# 사용: bash import_alert_kind.sh <alert_kind_prod.csv 경로>
set -euo pipefail
CSV="$1"
DB=disaster_alert_eval_v5
docker cp "$CSV" postgres:/tmp/alert_kind_prod.csv
docker exec -i postgres psql -U postgres -d "$DB" -X -v ON_ERROR_STOP=1 <<'SQL'
CREATE TEMP TABLE ak (disaster_alert_id bigint, sn bigint, alert_kind varchar(16));
\copy ak FROM '/tmp/alert_kind_prod.csv' WITH CSV HEADER

-- 1) 운영 파일 상태
SELECT 'prod_file' AS what, coalesce(alert_kind,'(null)') AS k, count(*) FROM ak GROUP BY 2 ORDER BY 2;

-- 2) 사본 알림과 짝 맞추기: id 와 sn 이 둘 다 같아야 같은 알림으로 본다
SELECT 'match' AS what,
       count(*)                                         AS copy_alerts,
       count(ak.disaster_alert_id)                      AS matched_by_id,
       count(*) FILTER (WHERE ak.sn = d.sn)             AS id_and_sn_agree,
       count(*) FILTER (WHERE ak.alert_kind IS NULL)    AS prod_still_null
FROM disaster_alert d LEFT JOIN ak ON ak.disaster_alert_id = d.disaster_alert_id;

-- 3) 덮어쓰기 (id·sn 일치, 운영 값이 있는 것만)
UPDATE disaster_alert d SET alert_kind = ak.alert_kind
FROM ak WHERE ak.disaster_alert_id = d.disaster_alert_id AND ak.sn = d.sn AND ak.alert_kind IS NOT NULL;

-- 4) 결과
SELECT 'after' AS what, coalesce(alert_kind,'(null)') AS k, count(*) FROM disaster_alert GROUP BY 2 ORDER BY 2;
SQL

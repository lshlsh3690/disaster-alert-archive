-- 재난문자 목록/검색(searchAlerts)이 쓰는 두 경로에 인덱스가 없었다 (6만 건 기준 EXPLAIN ANALYZE).
--
-- 1) ORDER BY created_at DESC LIMIT 20 (필터 없는 기본 목록, 가장 호출이 많은 쿼리):
--    created_at 인덱스가 없어 6만 행 전체를 Seq Scan한 뒤 top-N heapsort 했다 (27.7ms).
--    인덱스를 타면 앞 20건만 읽고 끝난다 (0.05ms).
-- 2) disaster_alert_region 의 legal_district_code 조건 EXISTS (districtCode 필터, 관심지역 조회):
--    PK가 (disaster_alert_id, legal_district_code) 라 코드 선두 조회는 인덱스를 못 타고
--    69,032행을 Seq Scan 했다. 코드가 선두인 인덱스를 추가한다 (3.7ms → 0.06ms,
--    다건 매칭 코드 기준 4.5ms 에서도 Index Only Scan 으로 전환).
CREATE INDEX IF NOT EXISTS idx_disaster_alert_created_at
    ON disaster_alert (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_disaster_alert_region_district_code
    ON disaster_alert_region (legal_district_code, disaster_alert_id);

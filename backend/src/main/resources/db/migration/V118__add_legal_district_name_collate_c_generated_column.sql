-- countByRegion(/api/v1/alerts/stats)이 legal_district.name으로 GROUP BY할 때, DB 기본
-- collation(en_US.utf8, 로케일 인식 문자열 비교)을 그대로 쓰면 69,027행 조인 기준
-- EXPLAIN ANALYZE 실측 4,921ms — Postgres가 count(DISTINCT ...) 처리를 위해 GroupAggregate
-- 내부에서 항상 정렬을 거치는데, 그 정렬 키 비교가 로케일 인식이라 비싸다(docs/PERF_BASELINE.md
-- 7-2-1절에서 sigungu/breakdown에 대해 같은 원인을 이미 규명함).
--
-- COLLATE "C"(바이트 비교)로 바꾸면 79.8ms(약 61.7배)까지 떨어지지만, 쿼리 표현식 수준에서
-- `expr collate 'C'`(HQL 미지원, 파싱 에러) / CAST(expr AS custom_domain)(Hibernate가 CAST
-- 대상으로 커스텀 도메인을 인식 못 함) / SQL 함수로 감싸기(함수 반환값이 collation을 잃어
-- 성능 이점이 사라짐, 실측으로 확인) 전부 막혔다. 그래서 V117의 sigungu_name과 같은 패턴 —
-- 컬럼 자체에 COLLATE "C"를 박은 GENERATED 컬럼을 만들어 평범한 컬럼처럼 참조한다. 컬럼
-- 타입 수준의 collation이라 쿼리 쪽엔 별도 표현식이 필요 없고(QueryDSL이 컬럼을 그대로
-- select/groupBy/orderBy), name 컬럼 자체의 collation은 그대로라 다른 화면의 정렬에는
-- 영향이 없다.
ALTER TABLE legal_district
    ADD COLUMN name_collate_c VARCHAR(255) COLLATE "C"
    GENERATED ALWAYS AS (name) STORED;

-- countByRegion은 이 컬럼을 필터 조건이 아니라 GROUP BY/ORDER BY 키로만 쓰고, 드라이빙
-- 테이블도 disaster_alert 쪽이라(legal_district는 code PK로 조인) 이 인덱스가 countByRegion
-- 자체의 성능에 필수는 아니다(실측 108.6ms는 인덱스 없이 나온 수치). 그래도 V117
-- (sigungu_name)과 동일하게 생성해둔다 — 향후 이 컬럼으로 필터링하는 쿼리가 생기면 그때
-- 마이그레이션을 또 추가할 필요가 없도록.
CREATE INDEX IF NOT EXISTS idx_legal_district_name_collate_c
    ON legal_district (name_collate_c);

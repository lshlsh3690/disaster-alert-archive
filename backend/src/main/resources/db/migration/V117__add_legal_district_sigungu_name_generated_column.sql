-- DisasterAlertRepositoryImpl이 legal_district.name을 조회 시점마다 regexp_replace+split_part로
-- 파싱해 "시군구명"을 만들던 것이 sigungu/breakdown, weather-by-region 등의 병목이었다
-- (EXPLAIN ANALYZE로 확인: 69,027행 조인 결과에 대해 정렬 키 계산만으로 수 초 소요).
--
-- GENERATED ALWAYS ... STORED를 쓰는 이유: LegalDistrictService.saveAllLegalDistricts()가
-- Flyway 밖에서(CSV 임포트) legal_district에 신규 행을 계속 추가한다. 일반 컬럼 + 수동 백필이면
-- 이 서비스도 같이 고쳐야 하고 향후 다른 쓰기 경로가 생기면 다시 빠뜨릴 수 있다. GENERATED
-- 컬럼은 name이 어떤 경로로 쓰이든 Postgres가 자동으로 값을 재계산해 드리프트가 구조적으로
-- 불가능하다. regexp_replace/split_part는 PostgreSQL에서 IMMUTABLE이라 GENERATED 표현식
-- 제약을 통과한다.
--
-- 표현식은 DisasterAlertRepositoryImpl의 sigunguExpr()/getStatsSigungu/getStatsSigunguBreakdown/
-- getWeatherBySigungu에 있던 StringTemplate과 값이 100% 동일해야 한다 (마이그레이션 직후
-- 검증 쿼리로 기존 표현식과 전건 대조).
ALTER TABLE legal_district
    ADD COLUMN sigungu_name VARCHAR(255)
    GENERATED ALWAYS AS (
        CASE WHEN split_part(btrim(regexp_replace(name, '\s+', ' ', 'g')), ' ', 2) = ''
             THEN split_part(btrim(regexp_replace(name, '\s+', ' ', 'g')), ' ', 1)
             ELSE split_part(btrim(regexp_replace(name, '\s+', ' ', 'g')), ' ', 1) || ' ' ||
                  split_part(btrim(regexp_replace(name, '\s+', ' ', 'g')), ' ', 2)
        END
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_legal_district_sigungu_name
    ON legal_district (sigungu_name);

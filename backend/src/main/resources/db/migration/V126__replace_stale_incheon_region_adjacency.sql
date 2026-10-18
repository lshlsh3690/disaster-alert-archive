-- 인천 2026-07-01 분구 이후 위험도 BFS의 시군구 인접 그래프를 새 코드로 교체한다.
-- 경계 출처: 통계청 SGIS 행정동 경계를 보정한 vuski/admdongkor ver20260701
-- https://github.com/vuski/admdongkor/tree/master/ver20260701
-- 원자료 SGIS 및 가공 저장소 출처표시: https://github.com/vuski/admdongkor/blob/master/LICENSE-DATA
-- 행정동을 시군구 코드로 병합하고 ST_MakeValid 후 ST_Intersects/공유 경계를 검증했다.
-- 기존 frontend/public/sigungu.geojson에는 아직 새 인천 4개 구 도형이 없다.
-- 제물포-미추홀/연수/서해, 서해-미추홀/부평/계양/검단,
-- 검단-계양/김포는 공유 경계가 확인됨(총 9개 무향 간선).
-- 새 구와 주변 도형은 병합 후 모두 ST_IsValid = true(MakeValid 적용 여부와 무관). 원 행정동 도형도 무효 0건.
--
-- [재현 절차] PostGIS 가 있는 임시 DB 에서 아래대로 하면 아래 간선 9쌍과 교량 거리가 그대로 나온다
-- (이 마이그레이션 작성 시 PostGIS 16-3.4 로 확인).
--  1) 위 admdongkor ver20260701 의 전국 행정동 GeoJSON(FeatureCollection 3,558건, 좌표계 CRS84)을 받는다.
--     속성은 sgg(5자리 시군구 코드)·adm_nm·adm_cd2 를 쓴다.
--  2) 행정동마다 (sgg, adm_nm, geometry 의 GeoJSON 문자열) 한 행으로 적재한다: 테이블 admraw(sgg varchar(5), name text, geojson text).
--  3) 시군구 도형 256개를 만든다(sgg 가 새 인천 4개 구 코드 28125/28155/28275/28290 으로 이미 들어 있다):
--       CREATE TABLE sg AS
--       SELECT sgg AS code, ST_Union(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(geojson),4326))) AS geom
--       FROM admraw GROUP BY sgg;
--       CREATE INDEX ON sg USING gist (geom);
--     ST_MakeValid 는 방어용이다. 이 자료에서는 없이 합쳐도 256개가 모두 유효하고 같은 9쌍이 나오는 것을 확인했다.
--  4) 공유 경계 길이 — 새 구가 낀 접경 쌍을 모두 뽑는다:
--       SELECT a.code, b.code,
--              round((ST_Length(ST_Intersection(ST_Boundary(a.geom),ST_Boundary(b.geom))::geography)/1000)::numeric,1) AS shared_km
--       FROM sg a JOIN sg b ON a.code < b.code AND ST_Intersects(a.geom,b.geom)
--       WHERE a.code IN ('28125','28155','28275','28290') OR b.code IN ('28125','28155','28275','28290');
--     결과(km): 28125-28177 9.0 / 28125-28185 2.2 / 28125-28275 3.5 / 28177-28275 1.3 / 28237-28275 8.1 /
--     28245-28275 8.9 / 28245-28290 7.3 / 28275-28290 9.7 / 28290-41570 19.5 — 아래 pairs 의 주석과 같다.
--     영종구(28155)는 이 쿼리에 한 건도 나오지 않는다(육상 경계 없음).
--  5) 교량 예외의 거리: ST_Distance(a.geom::geography, b.geom::geography) — 28155-28275 약 715m, 28155-28185 약 5.6km.
--     이 간선은 경계 접촉이 아니라 설계 판단이다(아래 참고).
--     남동구(28200) 소실 간선 확인도 같은 ST_Distance 로 한다 — 28200-28275 약 231m, 28200-28125 약 1.46km, 28200-28290 약 10.3km
--     이고 4단계 쿼리에서 28200 은 새 구와 한 건도 나오지 않는다(모두 접촉 없음).
--
-- 영종구는 섬으로서 육상 경계가 없어 교량으로 연결한 간선 2개를 둔다. 경계 접촉이 아니라 설계 판단이다.
-- 영종-서해: 2026-01-05 개통 제3연륙교(영종 중산동-청라동), 폴리곤 간 최단 거리 약 715m
-- https://www.incheon.go.kr/IC010205/view?repDt=2026-01-04&repSeq=DOM_0000000013859567
--   V37 의 "섬은 최근접 본토 5km 이내면 다리로 연결" 규칙 안이다.
-- 영종-연수: 인천대교(영종도-송도국제도시), 폴리곤 간 최단 거리 약 5.6km
-- https://itour.incheon.go.kr/ssst/ssst/detail.do?cotId=ITD21122816341259711
--   V37 의 5km 기준을 넘지만 특수한 경우로 보고 인접으로 둔다(2026-10 사용자 결정).
--
-- 옛 중구(28110)는 제물포/영종으로, 서구(28260)는 서해/검단으로 분할되었다.
-- 따라서 단순 코드 치환은 오연결을 만들고, 동구(28140)는 제물포에 합쳐져
-- 중구-동구 간선은 사라진다. V112의 인접 그래프 정리 방식대로
-- 옛 코드가 포함된 양쪽 간선을 제거한다.
-- 남동구(28200)-옛 서구(28260) 간선도 대체 없이 사라진다. 새 서해·검단구는 남동구와 접하지 않는다
-- (서해와 약 231m, 제물포와 약 1.5km, 검단과 약 10km 떨어짐). 남동구는 미추홀·연수·부평·부천·시흥 간선이 남아 고립되지 않는다.
-- V123은 알림 이력과 이벤트 영향도(event_region_impact, 10자리 법정동코드)는 새 구 후보 전부로 복제하고 이벤트 주 지역 캐시는
-- 후보가 하나로 정해질 때만 갱신하지만, 5자리 시군구 코드 기반인
-- region_risk_index/history/daily 는 새 구 하나로 정할 근거가 없어 이관하지 않는다. 따라서
-- 이 테이블들을 V112처럼 일괄 삭제하거나 새 구 하나로 옮기지 않는다.
-- 남은 양수 source_score가 있다면 옛 코드의 홉0 위험도는 계속 남을 수 있으며,
-- 어느 새 구로 이관할지는 별도 근거가 필요하다(로컬 DB에는 해당 행 0건).

DELETE FROM region_adjacency
WHERE region_code IN ('28110','28140','28260')
   OR neighbor_code IN ('28110','28140','28260');

WITH pairs(a,b) AS (VALUES
    ('28125','28177'), -- 제물포-미추홀, 공유 경계 약 9.0km
    ('28125','28185'), -- 제물포-연수, 약 2.2km
    ('28125','28275'), -- 제물포-서해, 약 3.5km
    ('28177','28275'), -- 미추홀-서해, 약 1.3km
    ('28237','28275'), -- 부평-서해, 약 8.1km
    ('28245','28275'), -- 계양-서해, 약 8.9km
    ('28275','28290'), -- 서해-검단, 약 9.7km
    ('28245','28290'), -- 계양-검단, 약 7.3km
    ('28290','41570'), -- 검단-김포, 약 19.5km
    ('28155','28275'), -- 영종-서해: 제3연륙교, 경계 접촉 아님(폴리곤 간 약 715m, V37 5km 규칙 안)
    ('28155','28185')  -- 영종-연수: 인천대교, 경계 접촉 아님(폴리곤 간 약 5.6km, V37 5km 규칙 초과, 특수 사례로 유지)
)
INSERT INTO region_adjacency (region_code, neighbor_code)
SELECT a,b FROM pairs UNION ALL SELECT b,a FROM pairs
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM region_adjacency
               WHERE region_code IN ('28110','28140','28260')
                  OR neighbor_code IN ('28110','28140','28260')) THEN
        RAISE EXCEPTION 'V126 left stale Incheon adjacency codes';
    END IF;
    IF EXISTS (SELECT 1 FROM (VALUES ('28125'),('28155'),('28275'),('28290')) n(code)
               WHERE NOT EXISTS (SELECT 1 FROM region_adjacency a WHERE a.region_code = n.code)) THEN
        RAISE EXCEPTION 'V126 left a new Incheon district isolated';
    END IF;
    IF EXISTS (SELECT 1 FROM region_adjacency a
               WHERE (a.region_code IN ('28125','28155','28275','28290')
                  OR a.neighbor_code IN ('28125','28155','28275','28290'))
                 AND NOT EXISTS (SELECT 1 FROM region_adjacency b
                                 WHERE b.region_code = a.neighbor_code AND b.neighbor_code = a.region_code)) THEN
        RAISE EXCEPTION 'V126 Incheon adjacency is not bidirectional';
    END IF;
END $$;

-- 홈 대시보드 지도(KoreaMap25D)의 시군구/읍면동 보기용 경계 데이터를 만든다.
-- 입력(PostGIS, gis DB):
--   emd(emd_cd, emd_nm, geom)  국가공간정보포털 법정구역 읍면동 경계(LSMD_ADM_SECT_UMD, SRID 5186, 2026-09판)
--   off8(code8, name, is_active)  행정표준코드관리시스템 법정동코드 전체자료의 읍면동(8자리, 리 제외)
--   sg_names(code5, name)         같은 자료의 시군구 행(코드 앞 5자리 + 이름)
-- 투영: 기존 koreaSido.data.ts(시도 17개 SVG)와 겹치도록 같은 메르카토르 변환을 쓴다.
--   기존 경로에서 시도별 최대 다각형의 무게중심을 sido.geojson 과 비교해 구했다(오차 0.4px 미만):
--     x = 98.0650*lon - 12195.995 ,  y = -98.1811*mercY_deg + 4138.969
--   EPSG:3857(미터)에서의 아핀 계수로 환산한 값이 아래 :sx, :sy, :ox, :oy 이다.
-- 사용법은 README.md 참고.

DROP TABLE IF EXISTS map_emd, map_sg;

-- 1) 읍면동: 3857 -> 화면 좌표(px). ST_AsSVG 가 Y 부호를 뒤집어 출력하므로 y 는 부호를 반대로 둔다(e=+sy, yoff=-oy). 구지면(27710380)은 읍 승격으로 코드가 바뀌었다(구지읍 27710268).
CREATE TABLE map_emd AS
SELECT CASE WHEN e.emd_cd = '27710380' THEN '27710268' ELSE e.emd_cd END AS code8,
       ST_MakeValid(ST_Affine(ST_Transform(e.geom, 3857), :sx, 0, 0, :sy, :ox, -:oy)) AS geom
FROM emd e;

-- 2) 시군구: 읍면동을 코드 앞 5자리로 합친다(구가 있는 시는 구 단위, 예: 41111 수원시 장안구).
CREATE TABLE map_sg AS
SELECT left(code8, 5) AS code5, ST_UnaryUnion(ST_Collect(geom)) AS geom
FROM map_emd GROUP BY 1;

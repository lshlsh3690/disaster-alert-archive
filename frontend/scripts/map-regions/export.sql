-- build.sql 실행 후 지도용 JSON 한 덩어리를 stdout 으로 내보낸다(psql -At). generate.sh 가 호출한다.
--   :which = emd | sigungu
--   :sido  = emd 일 때만 필요한 시도 코드 앞 2자리(예: 11). 읍면동은 시도별 파일로 나눠 필요한 시도만 받게 한다.
--   :tol   = 경로 단순화 허용오차(px)
-- 좌표는 화면 좌표(px)이며 koreaSido.data.ts 와 같은 좌표계다. ST_AsSVG 가 Y 부호를 뒤집어 출력하므로
-- 저장된 Y 는 음수이고, 말풍선 기준점(p)과 경계 상자(b)는 부호를 되돌려 낸다.
\if :{?which}
\else
\echo 'which 변수가 필요합니다'
\quit
\endif
-- psql 은 실행되지 않는 CASE 분기의 :'sido' 도 치환하므로 sigungu 일 때를 위해 기본값을 둔다.
\if :{?sido}
\else
\set sido ''
\endif
SELECT CASE :'which'
  WHEN 'emd' THEN (
    SELECT json_build_object('regions', json_agg(json_build_object(
        'c', m.code8,
        'n', coalesce(o.name, ''),
        'd', ST_AsSVG(ST_SimplifyPreserveTopology(m.geom, :tol), 1, 1),
        'p', json_build_array(round(ST_X(pt)::numeric,1), round((-ST_Y(pt))::numeric,1))
      ) ORDER BY m.code8))
    FROM (SELECT code8, geom, ST_PointOnSurface(geom) AS pt FROM map_emd WHERE left(code8, 2) = :'sido') m
    LEFT JOIN off8 o ON o.code8 = m.code8 AND o.is_active)
  WHEN 'sigungu' THEN (
    -- b = [minX, minY, maxX, maxY] 화면 좌표 경계 상자. 시군구를 눌렀을 때 그 영역으로 확대하는 데 쓴다.
    -- 지도는 viewBox(0 0 682 714) 밖을 그리지 않으므로(독도는 x≈737 로 밖이다) b 와 p 는 viewBox 안에 보이는 부분으로
    -- 계산한다. 그러지 않으면 울릉군의 b 가 독도까지 포함해 확대 화면이 오른쪽으로 쏠린다.
    -- 저장된 Y 는 부호가 반대(음수)라 viewBox 는 y -714..0 으로 자른다. 전부 밖이면(해당 없음) 원래 도형을 쓴다.
    SELECT json_build_object('regions', json_agg(json_build_object(
        'c', s.code5,
        'n', coalesce(n.name, ''),
        'd', ST_AsSVG(ST_SimplifyPreserveTopology(s.geom, :tol), 1, 1),
        'p', json_build_array(round(ST_X(s.pt)::numeric,1), round((-ST_Y(s.pt))::numeric,1)),
        'b', json_build_array(round(ST_XMin(s.vis)::numeric,1), round((-ST_YMax(s.vis))::numeric,1),
                              round(ST_XMax(s.vis)::numeric,1), round((-ST_YMin(s.vis))::numeric,1))
      ) ORDER BY s.code5))
    FROM (SELECT code5, geom, vis, ST_PointOnSurface(vis) AS pt
          FROM (SELECT code5, geom,
                       CASE WHEN ST_IsEmpty(ST_Intersection(geom, ST_MakeEnvelope(0, -714, 682, 0, 3857))) THEN geom
                            ELSE ST_Intersection(geom, ST_MakeEnvelope(0, -714, 682, 0, 3857)) END AS vis
                FROM map_sg) v) s
    LEFT JOIN sg_names n ON n.code5 = s.code5)
END;

-- 화성시 만세구(41591)와 효행구(41593)는 sigungu.geojson 기준 공유 경계가 약 25.4km인데
-- region_adjacency 에 간선이 없었다. 만세구의 이웃이 평택시(41220) 하나뿐이라 위험도 확산이
-- 같은 시 안의 효행구에서 만세구로 직접 닿지 못하고 평택시를 거쳐 2홉으로 돌아갔다
-- (병점구 → 만세구는 3홉). 이 간선을 추가하면 효행구 → 만세구는 1홉, 병점구 → 만세구는
-- 2홉이 된다(RiskCalculationService.propagateEffective 의 다중소스 BFS 기준).
--
-- 근거: backend/scripts/adjacency-verify 의 비교 결과(경계 데이터 대조). 누락 원인은 만세구 도형이
-- PostGIS 기준 유효하지 않아(Hole lies outside shell) 최초 추출(V37) 때 빠졌을 가능성이 있으나
-- 확인하지 못했다. 이미 적용된 V37 은 수정하지 않고 양방향 2행만 추가한다.
INSERT INTO region_adjacency (region_code, neighbor_code) VALUES
('41591','41593'),
('41593','41591')
ON CONFLICT DO NOTHING;

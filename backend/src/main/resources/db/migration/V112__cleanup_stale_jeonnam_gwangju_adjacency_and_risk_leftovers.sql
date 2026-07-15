-- V111이 재난문자/이벤트/위험도 이력 데이터를 전남광주통합특별시(12xx) 코드로 이관했지만,
-- V110에서 "과거 이력 정합성" 목적으로 남겨뒀던 옛 광주광역시(29xx)·전라남도(46xx)
-- region_adjacency 행은 정리하지 않았다. 그 결과, 경남/전북 경계 시군구
-- (48850/52180/52190/52770/52790)가 신규 코드와 옛 코드 양쪽 인접 그래프에 동시에
-- 연결된 "다리" 노드로 남아, RiskCalculationService.propagateEffective() 가 신규 코드의
-- 위험도를 다리 노드를 거쳐 옛 코드 쪽으로 다시 흘려보내며 region_risk_index 등에
-- 옛 코드 행이 재생성되는 현상이 확인되었다.
--
-- V111 이후로는 옛 코드를 소스로 하는 데이터가 더 이상 존재하지 않으므로, 옛 인접 그래프는
-- 어떤 목적으로도 더 이상 필요하지 않다. 인접 그래프를 완전히 정리하고, 그 사이 누수로
-- 재생성된 옛 코드 잔여 행을 함께 제거한다.

DELETE FROM region_adjacency
WHERE region_code LIKE '29%' OR region_code LIKE '46%'
   OR neighbor_code LIKE '29%' OR neighbor_code LIKE '46%';

DELETE FROM region_risk_index
WHERE region_code LIKE '29%' OR region_code LIKE '46%';

DELETE FROM region_risk_daily
WHERE region_code LIKE '29%' OR region_code LIKE '46%';

DELETE FROM region_risk_history
WHERE region_code LIKE '29%' OR region_code LIKE '46%';

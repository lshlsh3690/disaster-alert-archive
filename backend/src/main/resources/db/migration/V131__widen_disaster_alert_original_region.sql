-- 2026-10-22 실측: 다수 읍면동을 한꺼번에 포함하는 광역 경보(예: 군산시 전역 호우경보, 60여개
-- 읍면동 나열)의 original_region 값이 VARCHAR(1000)을 넘겨 INSERT 자체가 실패했다. saveAll()의
-- 배치 저장 실패 시 한 건씩 재시도하는 fallback이 있지만 같은 트랜잭션 안에서 재시도하다 보니
-- 이미 rollback-only로 마크된 트랜잭션 때문에 같은 배치의 나머지 정상 알림까지 전부 저장 실패로
-- 번졌다(2026-08-31 10:28~22:18 구간 80건 유실의 원인). original_region은 조회/조인에 쓰이지
-- 않는 원본 참고용 텍스트라 길이 제한을 둘 이유가 없어 TEXT로 바꾼다.
ALTER TABLE disaster_alert ALTER COLUMN original_region TYPE TEXT;

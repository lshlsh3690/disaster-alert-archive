-- 광역 브로드캐스트 이벤트 표시 — blob 다리(bridge) 차단
--
-- 폭염/대설/한파 경보 등은 한 알림이 수십 시군구(최대 66)에 동시 발송된다.
-- 지역 hard 필터(시군구 교집합)만으로는 이 광역 알림이 "다리" 역할을 해
-- 멀리 떨어진 지역 이벤트들을 transitive 하게 한 덩어리로 연결한다(실데이터 검증).
--
-- 해결: 한 알림이 N개 시군구를 초과 커버하면 단독 이벤트(is_broadcast=true)로 두고,
--       이런 이벤트는 다른 알림의 머지 후보에서 제외한다.
--       광역 알림은 전체의 ~1.2% 뿐이라 국지 사건(산불 등) 클러스터링엔 영향 없음.
ALTER TABLE disaster_events ADD COLUMN is_broadcast BOOLEAN NOT NULL DEFAULT FALSE;

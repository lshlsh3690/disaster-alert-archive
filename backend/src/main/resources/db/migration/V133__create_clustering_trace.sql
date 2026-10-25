-- 클러스터링 판정 기록 (알림 1건당 1줄)
--
-- EventClusteringService 가 알림 하나를 어느 경로로, 어떤 후보·기준으로 판정해 어디에 배정했는지 판정 순간에
-- 남기는 기록이다. 관리자 성능 대시보드(docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ①)가
-- 검색·생성 단계의 N개 중 M개를 셀 때 읽는다. 운영 판정 로직은 이 표를 읽지 않는다.
--
-- alert_id : 알림 id (PK). 같은 알림을 다시 클러스터링(백필)하면 UPSERT 로 덮어써 알림당 1줄을 유지한다.
-- path : EMBEDDING / REGIONAL / EPISODIC / PERSON / GLOBAL_TYPE / BROADCAST / NO_REGION / SKIPPED
-- candidates : 같은 시군구 후보 [{"event":15918,"dist":0.387}]. 검색했는데 0건이면 [], 검색하지 않는 경로는 NULL
-- adjacent_candidates : 인접 시군구 후보(사고성 유형이 같은 시군구 병합에 실패해 실제 조회했을 때만), 그 외 NULL
-- threshold : 적용된 병합 거리 상한(임베딩 경로만)
-- llm_called / llm_result : LLM 폴백을 실제로 호출했는지, 고른 이벤트 id(없음이면 NULL)
-- result : MERGED / NEW / SKIPPED(비사건·본문 없음)
-- event_id : 최종 배정 이벤트(SKIPPED 는 NULL)
--
-- 외래 키를 두지 않는다. 이벤트는 재클러스터링·cross-region 병합 때 지워지는데, 이 표는 판정 순간의 기록이라
-- 지워진 이벤트 id 도 그대로 남아야 한다. 알림 삭제와도 묶지 않는다(기록만 독립적으로 보존).
CREATE TABLE clustering_trace (
    alert_id            BIGINT           PRIMARY KEY,
    path                VARCHAR(20)      NOT NULL,
    candidates          JSONB,
    adjacent_candidates JSONB,
    threshold           DOUBLE PRECISION,
    llm_called          BOOLEAN          NOT NULL DEFAULT FALSE,
    llm_result          BIGINT,
    result              VARCHAR(10)      NOT NULL,
    event_id            BIGINT,
    created_at          TIMESTAMP        NOT NULL DEFAULT NOW()
);

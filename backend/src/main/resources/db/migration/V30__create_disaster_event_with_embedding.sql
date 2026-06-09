-- 이벤트 클러스터링 인프라
--
-- 목표: 행정안전부 재난문자를 *개별* 알림이 아닌 *사건 단위* 로 묶기.
-- 예: "강원도 산불 4/5~4/10" 기간 N개 문자 → 1개 disaster_event.
--
-- 클러스터링 방식: OpenAI text-embedding-3-small (512차원) + pgvector HNSW + 지역 교집합 필터.
-- 시간창은 후보 검색 7일 슬라이딩 (최장 cooldown 기준). 유형별 정교 처리는 다음 PR.

-- 1. pgvector 확장 — vector 타입 및 거리 연산자 (<=>, <->, <#>) 제공
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. disaster_alert 에 임베딩 컬럼 추가
--
-- 512차원: OpenAI text-embedding-3-small 의 dimensions=512 옵션 결과.
-- 1536(기본) 대비 1/3 저장공간 + 시드 SQL 크기 1/3, 검색 품질 유사 (OpenAI 공식).
ALTER TABLE disaster_alert ADD COLUMN embedding VECTOR(512);

-- HNSW 인덱스 (cosine distance) — ANN 검색 O(log N), 60K row 규모에 적합.
-- m=16, ef_construction=64 는 pgvector 기본 권장값.
CREATE INDEX idx_disaster_alert_embedding_hnsw
    ON disaster_alert USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 3. disaster_events — 사건 메타데이터
--
-- 한 사건이 시간 흐름에 따라 N개 재난문자를 누적. 화면 표시용 event_title 자동 생성.
-- 다음 PR 에서 status(ACTIVE/MONITORING/RESOLVED) 컬럼 + 상태 전환 스케줄러 추가 예정.
CREATE TABLE disaster_events (
    id                    BIGSERIAL    PRIMARY KEY,
    -- 화면 표시용 제목. 예: "강원도 강릉시 산불 (2024-04-05)"
    event_title           VARCHAR(200) NOT NULL,
    -- 대표 재난 유형 (첫 알림의 disaster_type 캐시)
    primary_disaster_type VARCHAR(50),
    -- 대표 시군구 (첫 알림 첫 지역 코드)
    primary_region_code   VARCHAR(20),
    -- 대표 지역명 (제목 생성용 캐시)
    primary_region_name   VARCHAR(100),
    -- 사건 시작 시각 (첫 알림 created_at)
    first_alert_at        TIMESTAMP    NOT NULL,
    -- 사건 마지막 갱신 시각 (가장 최근 알림 created_at). 후보 검색 윈도우 / 상태 판단 기준.
    last_alert_at         TIMESTAMP    NOT NULL,
    -- 매핑 테이블 count(*) 매번 계산 안 하도록 캐시.
    alert_count           INT          NOT NULL DEFAULT 1,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 최근 이벤트 우선 정렬 (목록 API + 후보 검색 윈도우)
CREATE INDEX idx_disaster_events_last_alert_at
    ON disaster_events (last_alert_at DESC);

-- 지역별 필터 (지역 페이지 / 후보 검색 보조)
CREATE INDEX idx_disaster_events_primary_region
    ON disaster_events (primary_region_code);

-- 4. event_alert_mapping — 이벤트 ↔ 재난문자 N:M
--
-- 한 알림은 한 이벤트에만 속함 (PK 가 (event_id, alert_id) 이라도 alert_id 단독 UNIQUE 로 강제).
-- sequence_no 로 이벤트 내 순서 보존 (타임라인 UI 용).
CREATE TABLE event_alert_mapping (
    event_id    BIGINT    NOT NULL REFERENCES disaster_events (id)   ON DELETE CASCADE,
    alert_id    BIGINT    NOT NULL REFERENCES disaster_alert (disaster_alert_id) ON DELETE CASCADE,
    sequence_no INT       NOT NULL,
    added_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, alert_id),
    -- 한 alert 는 하나의 event 에만 속함
    CONSTRAINT uq_event_alert_mapping_alert UNIQUE (alert_id)
);

-- alert_id → event_id 역방향 lookup (재난문자 상세에서 소속 이벤트 표시)
CREATE INDEX idx_event_alert_mapping_alert
    ON event_alert_mapping (alert_id);

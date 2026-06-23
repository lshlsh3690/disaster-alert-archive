-- 임베딩 차원 상향: 512 → 1536
--
-- 배경: text-embedding-3-small 의 reduced 512 대신 full 1536 차원을 사용해 클러스터링 품질 개선.
-- 차원이 바뀌면 기존 512 벡터는 전부 무효 → NULL 처리 후 재임베딩(EventClusteringBackfillTool --backfill.embed-only).
--
-- 주의(순서 중요): vector 컬럼은 기존 행에 512 값이 남아 있으면 TYPE 변경이 실패한다.
--   1) HNSW 인덱스 드롭  2) 전 행 embedding NULL  3) 컬럼 타입 vector(1536)  4) 인덱스 재생성.
-- pgvector HNSW 최대 2000차원 → 1536 OK. 인덱스는 NULL 상태에서 생성(빈 인덱스) → 재임베딩이 채우며 점증 빌드.

DROP INDEX IF EXISTS idx_disaster_alert_embedding_hnsw;

UPDATE disaster_alert SET embedding = NULL;

ALTER TABLE disaster_alert ALTER COLUMN embedding TYPE vector(1536);

CREATE INDEX idx_disaster_alert_embedding_hnsw
    ON disaster_alert USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

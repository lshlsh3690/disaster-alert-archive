-- 이벤트 클러스터링 평가용 정답 라벨
--
-- 두 재난문자가 "같은 사건"인지 사람이 판정한 쌍 단위 라벨. 평가 서비스(profile=eval)가 읽어
-- 현재 이벤트 배정과 비교해 pairwise 정밀도·재현율·F1 등을 계산한다. 운영 코드 경로에서는 쓰지 않는다.
--
-- label : SAME(같은 사건) / DIFF(다른 사건) / UNSURE(판단 불가 — 지표에서 제외)
-- stratum : GENERAL(일반) / BOUNDARY(경계) / TRAP(함정) — 평가 셋 구성 층
-- sub_type : 층 안의 표본 추출 유형(예: general_same, trap_template_advisory)
-- dataset : 평가 셋 이름(셋을 여러 개 두고 비교할 수 있게)
CREATE TABLE evaluation_label (
    id          BIGSERIAL    PRIMARY KEY,
    dataset     VARCHAR(50)  NOT NULL,
    alert_a_id  BIGINT       NOT NULL REFERENCES disaster_alert (disaster_alert_id) ON DELETE CASCADE,
    alert_b_id  BIGINT       NOT NULL REFERENCES disaster_alert (disaster_alert_id) ON DELETE CASCADE,
    label       VARCHAR(10)  NOT NULL,
    stratum     VARCHAR(10)  NOT NULL,
    sub_type    VARCHAR(40)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_evaluation_label_label   CHECK (label   IN ('SAME', 'DIFF', 'UNSURE')),
    CONSTRAINT ck_evaluation_label_stratum CHECK (stratum IN ('GENERAL', 'BOUNDARY', 'TRAP')),
    CONSTRAINT ck_evaluation_label_order   CHECK (alert_a_id <> alert_b_id),
    CONSTRAINT uq_evaluation_label_pair    UNIQUE (dataset, alert_a_id, alert_b_id)
);

-- 평가 셋 단위 조회
CREATE INDEX idx_evaluation_label_dataset ON evaluation_label (dataset);

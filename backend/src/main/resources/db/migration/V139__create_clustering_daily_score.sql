-- 클러스터링 일일 채점 결과 (날짜당 1줄) + 판정한 쌍 (쌍당 1줄)
--
-- 매일 그날 발송된 사건(alert_kind = 'INCIDENT') 알림 X 와, X 보다 앞선 168시간 안의 사건 알림 Y 를 짝지어
-- 판사(LLM)에게 "같은 사건인가"를 묻고, 판사 판정을 정답으로 보고 시스템의 묶음이 맞았는지 센 결과다.
-- 설계는 specs/006-clustering-evaluation/plan.md 4·5절.
--
-- 비율·점수는 저장하지 않는다. 아래 숫자에서 계산해야 분모를 잃지 않는다.
--
-- clustering_daily_score
--   score_date : 채점한 날(그날 created_at 이 KST 00:00 이상 다음날 00:00 미만인 사건 알림 기준). PK
--   incident_count : 그날 사건 알림 수
--   pair_count : 판정한 쌍 수(UNSURE·형식 실패 포함)
--   correctly_merged / over_merged / missed : PairCounter 가 센 맞게 묶음 / 과병합 / 과소병합 쌍 수
--   unsure_count / format_failure_count : 점수 분모에서 뺀 쌍 수(판사가 모른다고 답함 / 응답이 한 단어 형식이 아님)
--   judge_version : 판사 프롬프트 버전
--   agreement_* : 그 버전의 272쌍 일치율(같은 사건 쌍·다른 사건 쌍 각각 분자/분모). 상수가 바뀌어도 그날 값을 알도록 복사해 둔다
--   prompt_tokens / completion_tokens : 비용 확인용
--   scored_at : 채점한 시각
CREATE TABLE clustering_daily_score (
    score_date           DATE      PRIMARY KEY,
    incident_count       INT       NOT NULL,
    pair_count           INT       NOT NULL,
    correctly_merged     INT       NOT NULL,
    over_merged          INT       NOT NULL,
    missed               INT       NOT NULL,
    unsure_count         INT       NOT NULL,
    format_failure_count INT       NOT NULL,
    judge_version        VARCHAR(16) NOT NULL,
    agreement_same_num   INT       NOT NULL,
    agreement_same_den   INT       NOT NULL,
    agreement_diff_num   INT       NOT NULL,
    agreement_diff_den   INT       NOT NULL,
    prompt_tokens        BIGINT    NOT NULL,
    completion_tokens    BIGINT    NOT NULL,
    scored_at            TIMESTAMP NOT NULL
);

-- clustering_daily_score_pair
--   alert_a < alert_b : 같은 쌍이 두 방향으로 들어가지 않게 작은 id 를 a 에 둔다
--   judge_verdict : SAME / DIFF / UNSURE / FORMAT_FAILURE
--   system_same_event : 채점 시점에 시스템이 두 알림을 같은 이벤트에 넣었는가
--   event_a / event_b : 채점 시점의 시스템 이벤트 id(미배정이면 NULL). 재클러스터링으로 이벤트가 지워져도 당시 상태를
--                       남기려고 외래 키를 걸지 않는다
--   disagreement : 시스템 묶음 + 판사 DIFF = OVER_MERGED, 시스템 분리 + 판사 SAME = MISSED, 그 밖은 NULL
CREATE TABLE clustering_daily_score_pair (
    id                BIGSERIAL   PRIMARY KEY,
    score_date        DATE        NOT NULL REFERENCES clustering_daily_score (score_date),
    alert_a           BIGINT      NOT NULL,
    alert_b           BIGINT      NOT NULL,
    judge_verdict     VARCHAR(16) NOT NULL,
    system_same_event BOOLEAN     NOT NULL,
    event_a           BIGINT,
    event_b           BIGINT,
    disagreement      VARCHAR(16),
    CONSTRAINT uq_clustering_daily_score_pair UNIQUE (score_date, alert_a, alert_b),
    CONSTRAINT ck_clustering_daily_score_pair_order CHECK (alert_a < alert_b),
    CONSTRAINT ck_clustering_daily_score_pair_verdict CHECK (judge_verdict IN ('SAME', 'DIFF', 'UNSURE', 'FORMAT_FAILURE')),
    CONSTRAINT ck_clustering_daily_score_pair_disagreement CHECK (disagreement IN ('OVER_MERGED', 'MISSED'))
);

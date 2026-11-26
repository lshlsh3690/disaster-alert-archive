-- 판사(LLM as a judge) 일치율 측정 기록과 쌍별 판정을 저장한다(specs/014-judge-agreement-db).
--
-- 예전에는 판사 버전별 일치율(v2 127/141 · 74/81 등)을 코드 상수(JudgeVersionCatalog)에 손으로 적었고, 근거는 마크다운
-- 리포트뿐이었다. 라벨을 관리자 화면에서 고쳐도 일치율은 그대로였다. 이제 측정마다 쌍별 판사 판정을 남기고, 일치율은
-- 저장하지 않고 현재 사람 라벨(evaluation_pair_label)과 맞대어 그때그때 계산한다 — 판사 판정은 라벨과 무관해서
-- 라벨이 바뀌어도 판사를 다시 부르지 않고(무료로) 다시 셀 수 있다.
--
-- 011(보류, 미커밋) 설계의 두 테이블 이름을 그대로 쓰되, 앱 안 재측정 버튼을 전제로 한 열(상태 RUNNING/ABORTED, 비교 버전,
-- 승인자·예상 비용, 진행 수, 이유별 제외 수)은 넣지 않는다. 측정은 평가 도구(CLI)가 판정을 다 마친 뒤 한 번에 저장한다.
--
-- 알림(disaster_alert)에는 외래 키를 걸지 않는다 — 시드(V146)가 알림이 없는 DB(테스트·새 개발 DB)에서도 들어가야 하고,
-- 측정 기록은 그때의 사실이라 알림 삭제와 묶을 이유가 없다. 알림이 지워지면 라벨 쪽이 CASCADE 로 사라져 계산에서 빠진다.
-- 측정 기록은 저장 뒤 고치지 않는다(다시 재면 새 행).

CREATE TABLE judge_agreement_run (
    id                BIGSERIAL     PRIMARY KEY,
    judge_version     VARCHAR(16)   NOT NULL,            -- JudgeVersionCatalog 의 버전 이름(v1, v2 …)
    model             VARCHAR(32)   NOT NULL,            -- 측정 때 모델
    dataset           VARCHAR(50)   NOT NULL,            -- 평가 셋 이름(evaluation_pair_label.dataset)
    origin            VARCHAR(16)   NOT NULL,            -- LEGACY_IMPORT(시드로 옮김) / CLI(평가 도구)
    finished_at       TIMESTAMP     NOT NULL,            -- 측정을 마친 시각(KST)
    prompt_tokens     BIGINT        NOT NULL,
    completion_tokens BIGINT        NOT NULL,
    estimated_usd     NUMERIC(10,4) NOT NULL,            -- 토큰 × 그때 단가, 캐시 할인을 빼지 않은 상한
    has_pair_verdicts BOOLEAN       NOT NULL,            -- 쌍별 판정 행이 있는가(합계만 옮긴 v1 만 false)
    legacy_same_num   INT,                               -- 쌍별 판정이 없을 때만 쓰는 합계 일치율
    legacy_same_den   INT,
    legacy_diff_num   INT,
    legacy_diff_den   INT,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_judge_agreement_run_origin CHECK (origin IN ('LEGACY_IMPORT', 'CLI')),
    -- 일치율 출처는 둘 중 하나만: 쌍별 판정(계산) 또는 합계(그대로)
    CONSTRAINT ck_judge_agreement_run_legacy CHECK (
        (has_pair_verdicts AND legacy_same_num IS NULL AND legacy_same_den IS NULL
                           AND legacy_diff_num IS NULL AND legacy_diff_den IS NULL)
        OR (NOT has_pair_verdicts AND legacy_same_num IS NOT NULL AND legacy_same_den IS NOT NULL
                                  AND legacy_diff_num IS NOT NULL AND legacy_diff_den IS NOT NULL))
);

-- 버전별 최신 측정 조회(finished_at 이 같으면 id 큰 것)
CREATE INDEX idx_judge_agreement_run_version_latest
    ON judge_agreement_run (judge_version, finished_at DESC, id DESC);

CREATE TABLE judge_agreement_run_pair (
    id       BIGSERIAL   PRIMARY KEY,
    run_id   BIGINT      NOT NULL REFERENCES judge_agreement_run (id),
    alert_a  BIGINT      NOT NULL,                       -- 작은 알림 id(정규화), 알림 외래 키 없음
    alert_b  BIGINT      NOT NULL,
    verdict  VARCHAR(16) NOT NULL,                       -- 판사 답(SAME/DIFF/UNSURE/FORMAT_FAILURE) 또는 호출 실패
    CONSTRAINT ck_judge_agreement_run_pair_order   CHECK (alert_a < alert_b),
    CONSTRAINT ck_judge_agreement_run_pair_verdict CHECK (verdict IN ('SAME', 'DIFF', 'UNSURE', 'FORMAT_FAILURE', 'CALL_FAILURE')),
    CONSTRAINT uq_judge_agreement_run_pair UNIQUE (run_id, alert_a, alert_b)
);

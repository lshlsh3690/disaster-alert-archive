-- 평가 셋 라벨을 관리자 화면에서 조회·수정할 수 있게 evaluation_label 을 넓힌다.
--
-- 예전에는 라벨이 backend/evaluation/datasets/*.json 파일에만 있었다. 이제 운영 DB 로 옮겨 관리자가 보고 고치고,
-- 라벨을 바꿀 때마다 사유와 이력(evaluation_label_history)을 남긴다. 평가 도구가 읽는 정답이 바뀌는 일이라
-- "누가·언제·왜 바꿨는지"를 추적할 수 있어야 한다.
--
-- 기존 행은 지우지 않는다 — 이미 들어 있을 수 있는 행을 살리려고 새 열은 모두 NULL 을 허용하고,
-- JSON 에는 없는 stratum·sub_type 은 NOT NULL 을 풀어 둔다(층 구분은 관리자 화면에서 쓰지 않는다).

-- 1) 층 제약 해제 — 새로 옮기는 라벨은 stratum·sub_type 없이 들어온다
ALTER TABLE evaluation_label DROP CONSTRAINT ck_evaluation_label_stratum;
ALTER TABLE evaluation_label ALTER COLUMN stratum  DROP NOT NULL;
ALTER TABLE evaluation_label ALTER COLUMN sub_type DROP NOT NULL;

-- 2) 관리자 화면용 열
-- q            : 평가 셋 문항 번호(JSON 의 q)
-- relation     : 두 알림의 지역 관계 — 같은 시군구 / 인접 시군구
-- criteria_date: 라벨을 매길 때의 판정 기준 시점(ClusteringCriteria.VERSION_DATE) — 기준이 바뀐 뒤 라벨과 구분하려고
-- updated_by/at: 마지막으로 라벨을 고친 회원과 시각(고친 적 없으면 NULL)
ALTER TABLE evaluation_label ADD COLUMN q             INT;
ALTER TABLE evaluation_label ADD COLUMN relation      VARCHAR(16);
ALTER TABLE evaluation_label ADD COLUMN criteria_date DATE;
ALTER TABLE evaluation_label ADD COLUMN updated_by    BIGINT;
ALTER TABLE evaluation_label ADD COLUMN updated_at    TIMESTAMP;
ALTER TABLE evaluation_label ADD CONSTRAINT ck_evaluation_label_relation
    CHECK (relation IN ('SAME_SIGUNGU', 'ADJACENT_SIGUNGU'));

-- 3) 라벨 변경 이력 — 바꿀 때마다 한 줄(이전·새 라벨, 사유, 수정자, 시각). 라벨 행이 지워지면 이력도 함께 지운다
CREATE TABLE evaluation_label_history (
    id          BIGSERIAL    PRIMARY KEY,
    label_id    BIGINT       NOT NULL REFERENCES evaluation_label (id) ON DELETE CASCADE,
    old_label   VARCHAR(10)  NOT NULL,
    new_label   VARCHAR(10)  NOT NULL,
    reason      TEXT         NOT NULL,
    changed_by  BIGINT       NOT NULL,
    changed_at  TIMESTAMP    NOT NULL,
    CONSTRAINT ck_evaluation_label_history_old CHECK (old_label IN ('SAME', 'DIFF', 'UNSURE')),
    CONSTRAINT ck_evaluation_label_history_new CHECK (new_label IN ('SAME', 'DIFF', 'UNSURE'))
);

-- 라벨 하나의 이력 조회
CREATE INDEX idx_evaluation_label_history_label ON evaluation_label_history (label_id);

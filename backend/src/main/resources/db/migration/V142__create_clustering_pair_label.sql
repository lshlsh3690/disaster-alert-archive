-- 불일치 쌍 사람 라벨 (쌍당 1줄, 덮어쓰기)
--
-- 관리자 대시보드에서 일일 채점이 "시스템과 판사가 어긋났다"고 기록한 쌍(clustering_daily_score_pair.disagreement)을
-- 사람이 보고 "같은 사건 / 다른 사건 / 모르겠음"으로 매긴 결과다. 라벨은 (1) 이 쌍에서 판사와 시스템 중 누가 틀렸는지,
-- (2) 판사 버전별 어려운 쌍 성적, (3) 이후 평가 도구의 정답 묶음으로 쓰인다. 설계는 specs/009-clustering-pair-labeling.
--
-- clustering_pair_label
--   alert_a < alert_b : 같은 쌍이 두 방향으로 들어가지 않게 작은 id 를 a 에 둔다(PairLabelSnapshot 이 이 제약에 기대어
--                       (alert_a, alert_b) 를 쌍의 정체로 쓴다)
--   label : SAME(같은 사건) / DIFF(다른 사건) / UNSURE(모르겠음)
--   criteria_date : 라벨을 매길 때의 기준 문서 버전 날짜(ClusteringCriteria.VERSION_DATE). 기준이 바뀐 뒤 라벨과 구분하려고 남긴다
--   created_by / created_at : 처음 매긴 회원 id·시각. 고쳐도 바뀌지 않는다
--   updated_by / updated_at : 마지막으로 고친 회원 id·시각(처음엔 created_* 와 같다). 평가 도구가 "기준 시각 이전 라벨"을 고를 때 쓴다
--   외래 키는 걸지 않는다 - 채점 쌍 기록·알림이 정리돼도 사람이 매긴 정답은 남아야 한다
CREATE TABLE clustering_pair_label (
    id            BIGSERIAL   PRIMARY KEY,
    alert_a       BIGINT      NOT NULL,
    alert_b       BIGINT      NOT NULL,
    label         VARCHAR(16) NOT NULL,
    criteria_date DATE        NOT NULL,
    created_by    BIGINT      NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_by    BIGINT      NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_clustering_pair_label UNIQUE (alert_a, alert_b),
    CONSTRAINT ck_clustering_pair_label_order CHECK (alert_a < alert_b),
    CONSTRAINT ck_clustering_pair_label_label CHECK (label IN ('SAME', 'DIFF', 'UNSURE'))
);

# 검증 절차: 판사 일치율 측정 결과를 DB 에 두고 현재 라벨로 계산한다

판사(유료) 호출은 이 절차 어디에도 없다. 측정 도구를 실제로 돌리는 것은 사용자 승인 뒤의 별개 작업이다.

## 0. 준비

- PR #358(라벨 JSON 읽기 삭제)이 머지된 develop 위에서 작업한다.
- 로컬 Postgres(`docker compose -f docker-compose.dev.yml up -d postgres redis`).
- 272쌍 라벨이 있는 로컬 사본: `disaster_alert_prod`(272행) 또는 `disaster_alert_eval_v6`(2026-11-25 에 272행 복사함).

## 1. 단위·통합 테스트

```bash
cd backend
./gradlew test --tests "*JudgeAgreementCalculatorTest" --tests "*JudgeAgreementServiceTest" \
  --tests "*ClusteringDailyScoreServiceTest" --tests "*ClusteringDashboard*"
```

- 계산기: 현재 라벨 기준 분자·분모, 라벨 변경 반영, 측정에 없는 쌍, 판사 UNSURE·형식 실패·호출 실패 제외.
- 시드: v2 쌍별 225행(SAME 134 · DIFF 88 · UNSURE 3), v1 합계 행(124/142 · 77/80, 쌍별 0행).

## 2. 시드 교차표가 리포트와 같은지 (SC-002, FR-009)

라벨이 있는 로컬 사본에 백엔드를 한 번 띄워 V145·V146 을 적용한 뒤(또는 `flyway migrate`), 아래 SQL 로 교차표를 본다.

```sql
WITH l AS (
  SELECT LEAST(alert_a_id, alert_b_id) a, GREATEST(alert_a_id, alert_b_id) b, label
  FROM evaluation_pair_label WHERE dataset = 'cluster-eval-2026-11'
), p AS (
  SELECT p.alert_a a, p.alert_b b, p.verdict
  FROM judge_agreement_run_pair p JOIN judge_agreement_run r ON r.id = p.run_id
  WHERE r.judge_version = 'v2' AND r.origin = 'LEGACY_IMPORT'
)
SELECT l.label AS human, p.verdict AS judge, count(*)
FROM p JOIN l USING (a, b) GROUP BY 1, 2 ORDER BY 1, 2;
```

기대값: SAME→SAME 127, SAME→DIFF 14, SAME→UNSURE 3, DIFF→DIFF 74, DIFF→SAME 7. 측정에 없는 SAME/DIFF 라벨 쌍 0.

## 3. 대시보드 값 (SC-002)

로컬 백엔드를 라벨 있는 사본에 붙여 띄우고 관리자로 판사 버전 이력을 연다.

- v2: 같은 사건 127/141 (90.1%), 다른 사건 74/81 (91.4%), 측정일 2026-11-18, 측정에 없는 쌍 표시 없음.
- v1: 124/142 (87.3%), 77/80 (96.3%), "합계만(라벨 변경 미반영)" 표시.

## 4. 라벨 변경 반영 (SC-003)

1. 평가 셋 라벨 화면에서 v2 가 SAME 으로 판정한 SAME 쌍 하나를 DIFF 로 바꾼다.
2. 판사 버전 이력을 다시 연다 → v2 같은 사건 126/140, 다른 사건 74/82.
3. 그동안 판사 호출 로그(`판사` / OpenAI 호출)가 없는지 확인한다.
4. 라벨을 원래대로 돌린다.

## 5. 측정 도구 동작 (판사 호출 없이)

- `--eval.judge-agreement --eval.report=x.md` → 판사 호출 전에 오류로 끝나는지.
- `--eval.judge-agreement --eval.pair-labels-as-of=...` → 판사 호출 전에 오류로 끝나는지.
- 저장 흐름은 판사를 가짜로 바꾼 통합 테스트로 확인한다(측정 1행 + 쌍별 행, `origin='CLI'`).

## 6. 운영 반영 (SC-004, FR-013)

1. **배포 전(사용자)**: 운영 DB 에 `backend/evaluation/datasets/pair_label_data_2026_11.sql` 을 DBeaver 로 실행 → `SELECT count(*) FROM evaluation_pair_label WHERE dataset='cluster-eval-2026-11'` 가 272.
2. 배포 → Flyway 가 V145·V146 적용.
3. 대시보드 판사 버전 이력이 3절과 같은지.
4. 다음 날 01:00 일일 채점 기록이 생기고 일치율이 127/141 · 74/81 인지.
5. 라벨을 넣기 전에 배포됐다면: 그날 채점이 warn 과 함께 건너뛰어지고(비용 없음), 라벨을 넣은 다음 실행이 빠진 날을 채우는지.

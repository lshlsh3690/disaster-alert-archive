# 데이터 모델: 판사 일치율 측정

새 테이블 2개(V145), 시드(V146). 기존 `evaluation_pair_label`(V144)·`clustering_daily_score` 는 읽기만 하고 고치지 않는다. 알림은 ID 로만 가리키고 외래 키를 걸지 않는다(research R3).

## 1. `judge_agreement_run` — 측정 (엔티티 `JudgeAgreementRun`)

| 열 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `judge_version` | VARCHAR(16) NOT NULL | 카탈로그 버전 이름(`v1`, `v2` …) |
| `model` | VARCHAR(32) NOT NULL | 측정 때 모델(카탈로그 모델을 복사) |
| `dataset` | VARCHAR(50) NOT NULL | 평가 셋 이름(`evaluation_pair_label.dataset` 과 같은 길이) |
| `origin` | VARCHAR(16) NOT NULL | `LEGACY_IMPORT`(시드로 옮김) / `CLI`(측정 도구) |
| `finished_at` | TIMESTAMP NOT NULL | 측정을 마친 시각(KST) |
| `prompt_tokens` / `completion_tokens` | BIGINT NOT NULL | 측정 전체 토큰 |
| `estimated_usd` | NUMERIC(10,4) NOT NULL | 토큰 × 그때 단가. 캐시 할인을 빼지 않은 상한 |
| `has_pair_verdicts` | BOOLEAN NOT NULL | 쌍별 판정 행이 있는가(v1 시드만 false) |
| `legacy_same_num` / `legacy_same_den` / `legacy_diff_num` / `legacy_diff_den` | INT NULL | 쌍별 판정이 없을 때만 채우는 합계 일치율 |
| `created_at` | TIMESTAMP NOT NULL DEFAULT now() | |

- CHECK: `has_pair_verdicts = true` 이면 `legacy_*` 4개 모두 NULL, false 이면 4개 모두 NOT NULL.
- CHECK: `origin IN ('LEGACY_IMPORT','CLI')`.
- 인덱스: `(judge_version, finished_at DESC, id DESC)` — 버전별 최신 측정 조회.
- 엔티티는 생성 팩터리만 있고 수정 메서드가 없다(FR-012). 다시 재면 새 행이다.

## 2. `judge_agreement_run_pair` — 쌍별 판정 (엔티티 `JudgeAgreementRunPair`)

| 열 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `run_id` | BIGINT NOT NULL REFERENCES `judge_agreement_run(id)` | 같은 맥락 안의 객체 참조 허용(헌법 VII)이나, 조회는 run_id 로 묶어 읽는다 |
| `alert_a` / `alert_b` | BIGINT NOT NULL | 정규화(`alert_a < alert_b`). 알림 외래 키 없음 |
| `verdict` | VARCHAR(16) NOT NULL | `SAME` / `DIFF` / `UNSURE` / `FORMAT_FAILURE` / `CALL_FAILURE` |

- CHECK: `alert_a < alert_b`, `verdict IN (...)`.
- UNIQUE: `(run_id, alert_a, alert_b)`.
- `CALL_FAILURE` 는 `JudgeVerdict` enum 에 넣지 않는다 — 판사가 낸 답이 아니라 호출 장애라서다. 엔티티는 문자열 상수나 별도 저장용 enum 으로 다루고, 계산기는 호출 실패를 따로 센다.
- 쌍별 토큰은 저장하지 않는다(측정 합계만 쓴다).

## 3. 시드 (V146)

| 측정 | 값 |
|---|---|
| v1 | `gpt-4o`, `cluster-eval-2026-11`, `LEGACY_IMPORT`, 2026-11-17 14:30:28, 토큰 343,825 / 459, $0.8640, `has_pair_verdicts=false`, legacy 124/142 · 77/80 |
| v2 | `gpt-4o-mini`, `cluster-eval-2026-11`, `LEGACY_IMPORT`, 2026-11-18 09:06:05, 토큰 340,740 / 453, $0.0514, `has_pair_verdicts=true`, 쌍별 225행 |

- v2 쌍별 225행은 고정 VALUES(research R4). 판정 분포 SAME 134 · DIFF 88 · UNSURE 3.
- 시드는 `run_id` 를 하드코딩하지 않고 `INSERT … RETURNING` 대신 `(SELECT id FROM judge_agreement_run WHERE judge_version='v2' AND origin='LEGACY_IMPORT')` 로 연결한다.
- 마이그레이션 주석에 출처(리포트 파일·커밋)와 v2 리포트 머리의 버전 표기 차이(R6)를 적는다.

## 4. 계산 값 (저장하지 않음)

### 버전 일치율 (`JudgeAgreementCalculator`, 순수)

입력: 쌍별 판정 목록 `(a, b, verdict)`, 현재 라벨 맵 `(min(a,b), max(a,b)) → SAME|DIFF|UNSURE`.

| 출력 | 규칙 |
|---|---|
| `Agreement(sameNum, sameDen, diffNum, diffDen)` | 라벨 SAME 이고 판정 SAME/DIFF 인 쌍이 같은 사건 분모, 그중 SAME 이 분자. DIFF 도 같다 |
| 판사 UNSURE / 형식 실패 / 호출 실패 수 | 라벨이 SAME/DIFF 인 쌍 중 그 판정 수(분모 밖) |
| 측정에 없는 쌍 수(`unmeasuredPairs`) | 라벨이 SAME/DIFF 인데 판정이 없는 쌍 |
| 라벨 없는 판정 수 | 판정은 있는데 라벨이 사라졌거나 UNSURE 가 된 쌍(계산에서 뺌, 참고용) |

쌍별 판정이 없는 측정은 계산기를 거치지 않고 `legacy_*` 를 그대로 쓰고 `recomputable=false` 로 표시한다.

### 버전별 현재 일치율 (`JudgeAgreementService`)

- 그 버전의 최신 측정(`finished_at` 내림차순, 같으면 id 내림차순) 하나를 쓴다.
- 쌍별 판정이 있는데 그 측정의 `dataset` 라벨이 DB 에 0행이면 "계산할 수 없음"(Optional.empty).

## 5. 바뀌는 기존 코드

| 대상 | 변경 |
|---|---|
| `JudgeVersionCatalog.Entry` | `sameNum…diffDen`, `measuredOn` 제거. 버전·모델·단가·`estimateUsd` 유지 |
| `ClusteringJudge.agreementOf` | 제거(`Agreement` 레코드는 유지) |
| `ClusteringDailyScoreService.score` | 일치율을 `JudgeAgreementService.latest(PROMPT_VERSION)` 에서 받는다. 비면 지금처럼 warn 후 건너뜀 |
| `ClusteringDashboardService.judgeVersions` | 카탈로그 순서로 돌며 버전마다 서비스 값을 붙인다. 응답에 `recomputable`, `unmeasuredPairs` 추가, `measuredOn` 은 최신 측정의 `finished_at` 날짜 |
| `JudgeAgreementRunner` | 리포트 대신 `saveRun` + 콘솔 요약 |
| `JudgeAgreementReport` | 삭제 |
| `EvaluationRunTool` | `--eval.judge-agreement` + `--eval.report` 동시 지정이면 오류 |

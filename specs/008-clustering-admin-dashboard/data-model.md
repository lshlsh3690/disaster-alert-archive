# 데이터 모델: 클러스터링 관리자 대시보드

새 테이블은 없다. 화면이 읽는 기존 데이터와, 그 위에서 계산하는 값의 규칙만 적는다.

## 읽는 기존 데이터

| 데이터 | 출처 | 화면에서 쓰는 필드 |
|---|---|---|
| 일일 채점 기록 | `clustering_daily_score`(날짜당 1행) | `score_date`, `incident_count`, `pair_count`, `correctly_merged`, `over_merged`, `missed`, `unsure_count`, `format_failure_count`, `judge_version`, `agreement_same_num/den`, `agreement_diff_num/den`, `prompt_tokens`, `completion_tokens`, `scored_at` |
| 불일치 쌍 | `clustering_daily_score_pair` 중 `disagreement` 가 OVER_MERGED·MISSED 인 행 | `score_date`, `alert_a`, `alert_b`, `judge_verdict`, `disagreement`, `event_a`, `event_b` |
| 구조 검사 기록 | `clustering_daily_check`(날짜당 1행) | `check_date`, `incident_count`, `unassigned_count`, `no_embedding_count` |
| 알림 요약 | `disaster_alert`(`disasteralert` 맥락 service 경유) | id, 원문, 발송 시각, 유형 |
| 판사 버전 | `JudgeVersionCatalog`(코드) | 버전, 모델, 같은 사건·다른 사건 일치율, 입력·출력 단가, 측정일 |

## 계산 값과 규칙 (`ClusteringDashboardMetrics`, 순수)

### 비율 (Ratio)
- 필드: 분자, 분모, 백분율(분모 > 0 일 때만).
- 규칙: 분모 0 → 백분율 없음, 화면에 "-". 0% 로 바꾸지 않는다.
- 맞게 묶은 비율 = 맞게 묶음 / (맞게 묶음 + 과병합). 빠짐없이 묶은 비율 = 맞게 묶음 / (맞게 묶음 + 과소병합). 둘 다 `PairCountResult` 정의 그대로.
- 점수 = 두 비율의 조화평균 = 2C / (2C + 과소병합 + 과병합)(`PairCountResult.score()`). 이 분모가 0 일 때만 계산할 수 없음 — 한쪽 비율만 계산할 수 없는 날(예: 맞게 묶음 0, 과병합 1)은 0점이다.

### 날짜 칸 상태 (DayStatus)
- `SCORED`: 기록 있음, 판정 쌍 ≥ 1.
- `NO_PAIRS`: 기록 있음, 판정 쌍 0(비율·점수 계산할 수 없음).
- `NO_RECORD`: 그날 기록 없음(채점 꺼짐·시작 전·실패).
- 요청 범위의 모든 날에 하나씩 만든다(빈 날 채우기).

### 추정 비용
- 프롬프트 토큰 × 입력 단가 + 완성 토큰 × 출력 단가, 단가는 그날 `judge_version` 의 `JudgeVersionCatalog` 값.
- 목록에 없는 버전이면 비용은 "알 수 없음"(토큰만 표시).
- 상한값(캐시 할인 미반영) — 화면에 표시.

### 판사 버전 기간
- 버전마다 일일 채점 기록에서 첫날·마지막 날, 채점한 날 수.
- `JudgeVersionCatalog` 에는 있지만 기록이 없는 버전 → "채점 기록 없음".
- 기록에는 있지만 목록에 없는 버전(이론상 없음) → 모델·일치율 "알 수 없음"으로 그래도 보여 준다.

### 구조 검사 강조
- 미배정 > 0 또는 임베딩 없음 > 0 인 날을 강조 대상으로 표시.

## 검증 규칙
- 조회 범위: 시작 ≤ 끝, 길이 ≤ 366일. 어기면 오류 코드로 거부.
- 불일치 쌍 페이지: `size` 1~100(기본 20), `kind` ∈ {OVER_MERGED, MISSED, 없음=전체}.

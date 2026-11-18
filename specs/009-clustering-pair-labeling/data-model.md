# 데이터 모델: 불일치 쌍 사람 라벨링

## 새 테이블 `clustering_pair_label` (V140)

| 열 | 형식 | 설명 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `alert_a` | BIGINT NOT NULL | 두 알림 중 작은 id |
| `alert_b` | BIGINT NOT NULL | 큰 id. `CHECK (alert_a < alert_b)` |
| `label` | VARCHAR(16) NOT NULL | `SAME` / `DIFF` / `UNSURE` (CHECK) |
| `criteria_date` | DATE NOT NULL | 라벨을 매길 때의 기준 시점(`ClusteringCriteria.VERSION_DATE`) |
| `created_by` | BIGINT NOT NULL | 처음 매긴 회원 id |
| `created_at` | TIMESTAMP NOT NULL | 처음 매긴 시각 |
| `updated_by` | BIGINT NOT NULL | 마지막으로 고친 회원 id(처음엔 `created_by` 와 같음) |
| `updated_at` | TIMESTAMP NOT NULL | 마지막으로 고친 시각 |

- `UNIQUE (alert_a, alert_b)` — 쌍마다 라벨 하나(R1).
- 외래 키 없음(R2).
- 상태 변경은 엔티티 메서드 `relabel(label, criteriaDate, memberId, now)` 로만 한다: `label`·`criteria_date`·`updated_by`·`updated_at` 을 바꾸고 `created_*` 는 그대로 둔다.

## 읽는 기존 테이블

- `clustering_daily_score_pair`(V139): `score_date`, `alert_a`, `alert_b`, `judge_verdict`, `system_same_event`, `event_a/b`, `disagreement`. 라벨과 `(alert_a, alert_b)` 로 이어진다.
- `clustering_daily_score`(V139): `score_date` → `judge_version`. 쌍의 판사 버전을 정한다(R6).

## 순수 규칙 (`DisagreementGrading`)

### 누가 틀렸는가 (`WrongSide`)

| 사람 라벨 | 판사 판정 | 시스템 같은 이벤트 | 결과 |
|---|---|---|---|
| SAME | SAME | 아니오(과소병합) | SYSTEM(시스템이 틀림) |
| SAME | DIFF | 예(과병합) | JUDGE(판사가 틀림) |
| DIFF | DIFF | 예(과병합) | SYSTEM |
| DIFF | SAME | 아니오(과소병합) | JUDGE |
| UNSURE | 무엇이든 | 무엇이든 | 판정 없음(null) |

일반 규칙: 판사 맞음 ⇔ `judge_verdict == label`, 시스템 맞음 ⇔ `system_same_event == (label == SAME)`. 불일치 쌍에서는 둘 중 하나만 맞는다.

### 판사 버전별 어려운 쌍 성적 (`HardPairGrade`)

- 입력: 지금 불일치인 채점 쌍 중 라벨이 있는 것 × 그 쌍의 판사 버전.
- 버전마다:
  - `judgeRight` = 판사가 맞은 쌍 / (SAME·DIFF 라벨 쌍) — `ClusteringDashboardMetrics.Ratio`
  - `systemRight` = 시스템이 맞은 쌍 / (SAME·DIFF 라벨 쌍)
  - `unsureCount` = UNSURE 라벨 쌍 수(분모에서 뺌)
- 분모가 0 이면 백분율 없음("아직 라벨 없음", spec FR-011).
- 같은 두 알림이 여러 날 불일치로 나오면(드묾) 날짜마다 한 번씩 센다 — 날짜마다 판사가 따로 판정했기 때문이다.
- 결과 순서는 `JudgeVersionCatalog` 순서(최신 먼저), 목록에 없는 버전은 뒤.

### 평가용 라벨 묶음 (`PairLabelSnapshot`, `evaluation` 패키지)

- 입력: 저장된 라벨 행 `(alert_a, alert_b, label, updated_at)` 목록, 기준 시각.
- 출력: `LabelJson.LabeledPair(alertA, alertB, label)` 목록 — 평가 도구가 라벨 파일에서 읽던 것과 같은 모양이라 이후 채점·리포트가 그대로 돈다.
- 규칙: `updated_at ≤ 기준 시각` 인 행만, `(alert_a, alert_b)` 오름차순. UNSURE 도 포함한다(채점에서 빠지는 것은 기존 평가 규칙 그대로).
- 0개면 평가 도구가 채점하지 않고 멈춘다(spec FR-014).
- 덮어쓰기 구조라 기준 시각 뒤에 고친 라벨은 빠진다(research R11).

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
- `disaster_alert`: 내보내기 항목의 원문·시각·유형. `DisasterAlertService.findAlertSummaries` 를 거친다(008 과 같음).

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

### 회귀 셋 항목 (`ExportItem`)

`cluster-eval-2026-11.json` 과 같은 모양 + 사람이 보는 필드:

```json
{
  "q": 1,
  "label": "DIFF",
  "criteriaDate": "2026-11-13",
  "scoreDate": "2026-11-19",
  "kind": "OVER_MERGED",
  "judgeVerdict": "DIFF",
  "a": { "id": 101, "at": "2026-11-19 09:00", "type": "호우", "message": "..." },
  "b": { "id": 205, "at": "2026-11-19 12:00", "type": "호우", "message": "..." }
}
```

- `LabelJson.parse` 는 `a.id`·`b.id`·`label` 만 읽는다. 나머지는 무시된다.
- 같은 쌍이 여러 날 불일치였어도 파일에는 한 번만(가장 최근 날짜 기준) 넣는다 — `LabelJson` 이 중복 쌍을 오류로 거절하기 때문이다.
- 원문이 없는 알림은 `message: null`, `at`·`type` 도 null.

# 데이터 모델: 클러스터링 평가의 대표 지표를 균형 정확도로

명세: [spec.md](spec.md), 결정 근거: [research.md](research.md).

## 1. 쌍 평가 결과 (`PairCountResult`, 순수 record)

| 항목 | 지금 | 바뀐 뒤 |
|---|---|---|
| `correctlyMerged`, `missed`, `overMerged` | 있음 | 그대로 |
| `correctlySeparated` 맞게 나눔 | 없음 | 사람 다른 사건 + 시스템 다른 이벤트 |
| `excludedUnsure`, `excludedUnassigned` | 있음 | 그대로 |
| `sameEventPairs()` | 맞게 묶음 + 과소병합 | 그대로 |
| `differentEventPairs()` | 없음 | 과병합 + 맞게 나눔 |
| `completeness()` 빠짐없이 묶은 비율 | 있음 | 그대로 |
| `separation()` 섞지 않은 비율 | 없음 | 맞게 나눔 ÷ 다른 사건 쌍 |
| `balancedAccuracy()` | 없음 | (빠짐없이 묶은 비율 + 섞지 않은 비율) ÷ 2 — 대표 지표 |
| `purity()`, `score()` F1 | 있음 | 보조로 그대로 |
| `fromDailyCounts(...)` | 없음 | 기존 여섯 일일 집계값으로 같은 결과를 만드는 순수 팩토리 |

- 비율은 0~100, 분모가 0이면 empty다. 균형 정확도는 양쪽 분모가 모두 양수일 때만 개수에서 바로 계산한다.
- 구성비 불변성은 클래스별 배수를 다르게 해서 확인한다: (8, 2, 1, 9) → (16, 4, 1, 9)는 구성비 1:1 → 2:1이고 균형 정확도는 둘 다 85.0%다.

## 2. 판단 없는 기준선 (`TrivialBaselines`, 순수 클래스)

입력은 `PairCountResult` 하나이며, 모르겠음·미배정을 제외한 같은 쌍 집합을 쓴다.

| 기준선 | 맞게 묶음 | 과소병합 | 과병합 | 맞게 나눔 | 균형 정확도 |
|---|---|---|---|---|---|
| 전부 하나로 묶음 | 같은 사건 쌍 | 0 | 다른 사건 쌍 | 0 | 50% |
| 전부 따로 둠 | 0 | 같은 사건 쌍 | 0 | 다른 사건 쌍 | 50% |

두 분모가 모두 있을 때만 50%이며, 한쪽이 없으면 기준선도 계산 불가다.

## 3. 일일 채점 기록 (`clustering_daily_score`, 저장 형식 변경 없음)

`ClusteringDailyScore` 엔티티·`of(...)`·테이블 열은 바꾸지 않는다. 새 마이그레이션과 백필도 없다.

```text
correctlySeparated = pairCount
                   - correctlyMerged - missed - overMerged
                   - unsureCount - formatFailureCount
```

- 현재 `pairCount`는 UNSURE·형식 실패까지 포함한 `judged.size()`이며 두 알림 모두 배정된 쌍만 대상이다.
- 복원은 `PairCountResult.fromDailyCounts(pairCount, correctlyMerged, missed, overMerged, unsureCount, formatFailureCount)` 한 곳에서 수행한다. 서비스·엔티티·프론트에 식을 중복하지 않는다.
- 쌍별 기록이 없어도 날짜별 집계가 유효하면 복원한다. 완전한 쌍별 기록은 검증 자료로만 사용한다.
- 기존 집계와 복원 값의 음수 여부를 적용 전에 확인한다. 불일치는 임의로 0으로 보정하지 않고 원인을 확인한다(R5).
- 새 일일 로그는 `PairCounter`의 같은 정의를 사용한다. 로그를 위해 저장 형식이나 유료 호출을 추가하지 않는다.

## 4. 대시보드 날짜 항목 (`ClusteringDashboardDailyScoresResponse.Day`)

Java의 계산 불가 값은 null이지만 기존 `@JsonInclude(NON_NULL)`에 따라 JSON에서는 키가 생략된다.

| 필드 | 프론트 타입 | 규칙 |
|---|---|---|
| `correctlySeparated` | `correctlySeparated?: number` | 날짜별 기록이 있으면 복원한 개수(0 포함), 기록이 없으면 생략 |
| `separation` | `separation?: Ratio` | 날짜별 기록이 있으면 분자·분모를 가진 객체, 없으면 생략 |
| `balancedAccuracy` | `balancedAccuracy?: number` | 양쪽 분모가 양수이면 소수 1자리 값, 아니면 생략 |

- `Ratio`의 분모가 0이면 객체와 분자·분모는 유지하고 `percent` 키의 값만 null로 보낸다. 기존 Ratio 정책을 유지한다.
- `score`·`purity`·`completeness`와 기존 날짜 상태의 이름·뜻은 바꾸지 않는다.
- `SCORED`여도 유효한 클래스가 한쪽뿐이면 균형 정확도는 생략한다. 화면은 이를 계산 불가로 표시한다.

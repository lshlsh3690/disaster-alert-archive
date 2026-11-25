# 계약: 평가 리포트 요약과 일일 점수 API

## 1. 평가 리포트 요약 (`EvaluationRunTool --eval.report`, 마크다운)

요약 형식을 바꾸며 틀린 쌍 표의 내용·형식·순서는 유지한다. 회귀 비교에서만 `- 실행 시각: ` 한 줄을 제거하고, 나머지는 비교한다(quickstart 2, research R10).

```text
## 요약
- 균형 정확도 85.0% — 빠짐없이 묶은 비율과 섞지 않은 비율의 평균
- 빠짐없이 묶은 비율 80.0% — 같은 사건 100쌍 중 80쌍 묶음, 20쌍 과소병합 (과소병합율 20.0%)
- 섞지 않은 비율 90.0% — 다른 사건 50쌍 중 45쌍 따로 둠, 5쌍 과병합
- 보조: 맞게 묶은 비율 94.1% (묶은 85쌍 중 80쌍), F1 86.5점
- 판단 없는 기준선: 전부 하나로 묶음 균형 정확도 50.0% · F1 80.0점 / 전부 따로 둠 50.0% · F1 0.0점
- 제외: 모르겠음 10쌍, 이벤트 미배정 0쌍
```

- 예시는 형식 설명용이며 실제 평가 결과가 아니다(같은 사건 100쌍·다른 사건 50쌍).
- 분모가 0인 비율·균형 정확도·기준선 점수는 `-`로 표시한다. F1은 기존 식의 분모 규칙을 유지한다.
- 기존 리포트 테스트의 요약 문구 단정은 RED에서 이 형식으로 바꾼다. F1 수치와 틀린 쌍 표 단정은 보존하고 GREEN에서는 테스트를 수정하지 않는다.

## 2. 일일 점수 API (`GET /api/v1/admin/clustering/daily-scores?from&to`)

기존 `days[]`에 필드 세 개만 더한다. 예시 집계는 전체 26쌍 = 맞게 묶음 12 + 과병합 1 + 과소병합 2 + 맞게 나눔 9 + UNSURE 1 + 형식 실패 1이다. 맞게 나눔은 저장 열이 아닌 파생 값이다.

```json
{
  "date": "2026-11-20",
  "status": "SCORED",
  "pairCount": 26,
  "correctlyMerged": 12,
  "overMerged": 1,
  "missed": 2,
  "unsureCount": 1,
  "formatFailureCount": 1,
  "correctlySeparated": 9,
  "purity": { "numerator": 12, "denominator": 13, "percent": 92.3 },
  "completeness": { "numerator": 12, "denominator": 14, "percent": 85.7 },
  "separation": { "numerator": 9, "denominator": 10, "percent": 90.0 },
  "balancedAccuracy": 87.9,
  "score": 88.9
}
```

- Java의 null은 기존 `Day`의 `NON_NULL` 정책에 따라 JSON 키를 생략한다. 프론트 타입은 다음과 같고 키가 없으면 `undefined`다:

```typescript
correctlySeparated?: number;
separation?: Ratio;
balancedAccuracy?: number;
```

- 날짜별 기록이 있으면 `correctlySeparated`와 `separation`을 보낸다. `separation`의 분모가 0이면 Ratio 객체를 유지하고 `percent: null`을 보낸다.
- 같은 사건·다른 사건 쌍의 분모가 모두 양수일 때만 `balancedAccuracy`를 보낸다. 한쪽이라도 0이면 키를 생략하고 명시적인 `"balancedAccuracy": null`은 보내지 않는다.
- `NO_RECORD`는 기존처럼 날짜와 상태만 보내며 새 필드도 생략한다. `SCORED`여도 한쪽 클래스만 있으면 균형 정확도는 계산 불가다.
- `score`는 기존처럼 F1이며 화면 머리글만 "F1"로 바꾼다. 다른 엔드포인트와 기존 필드의 의미는 변경하지 않는다.

### 계산 불가 응답 예시

SAME 쌍 한 개만 있어 맞게 묶음 1, 다른 사건 쌍 0인 날짜의 관련 필드다. 균형 정확도 키는 없다.

```json
{
  "date": "2026-11-21",
  "status": "SCORED",
  "correctlySeparated": 0,
  "completeness": { "numerator": 1, "denominator": 1, "percent": 100.0 },
  "separation": { "numerator": 0, "denominator": 0, "percent": null }
}
```

## 3. 관리자 대시보드 화면 (`/admin/clustering`)

날짜별 점수 칸 순서: **균형 정확도** → 빠짐없이 묶은 비율 → 섞지 않은 비율 → 맞게 묶은 비율 → F1. 판사 일치율 칸은 유지한다.

- 균형 정확도 키가 없으면 "-"으로 표시한다. 0은 유효한 값이므로 truthy 검사로 비우지 않는다.
- Ratio는 객체 생략 또는 `percent: null`(분모 0)이면 계산 불가이며 화면에는 `-`만 보인다. 계산 가능한 Ratio만 `a/b (백분율)`로 보인다. API 객체는 분모 0이어도 분자·분모를 그대로 보낸다(2절).
- `NO_RECORD` 행은 기존의 "기록 없음" 처리를 유지한다.

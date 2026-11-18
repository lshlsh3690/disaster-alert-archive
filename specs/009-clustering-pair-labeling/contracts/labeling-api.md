# API 계약: 불일치 쌍 사람 라벨링

모두 `/api/v1/admin/clustering` 아래, ADMIN 전용(`@PreAuthorize("hasRole('ADMIN')")`). 응답은 `ApiResponse` 로 감싼다(`{success, code, message, data}`). 비로그인 401, 일반 회원 403. 프론트 요청은 `X-Auth-Required: "true"` 를 붙인다(008 과 같음).

## 1. 라벨 저장 — `PUT /disagreement-labels`

요청:

```json
{ "alertA": 205, "alertB": 101, "label": "DIFF" }
```

- `label`: `SAME` | `DIFF` | `UNSURE`(필수, `@Valid`).
- 순서는 상관없다. 서버가 작은 id 를 a 로 맞춘다.
- 회원 id 는 로그인 정보에서, 기준 시점은 서버 상수에서 붙인다(요청에 없음).

응답 `data`:

```json
{ "alertA": 101, "alertB": 205, "label": "DIFF", "criteriaDate": "2026-11-13", "updatedAt": "2026-11-19T10:12:30" }
```

오류:

| 상황 | HTTP | 코드 |
|---|---|---|
| `label` 이 없거나 모르는 값, id 누락 | 400 | C001(`INVALID_REQUEST`) 또는 검증 오류 |
| 두 id 가 같음 | 400 | C001 |
| 불일치 쌍으로 기록된 적 없는 쌍 | 404 | `CLUSTERING_PAIR_NOT_FOUND` |

## 2. 불일치 쌍 — `GET /disagreements` (008 확장)

추가 파라미터: `unlabeledOnly`(boolean, 기본 false) — true 면 라벨이 없는 쌍만.

쌍마다 추가 필드(라벨이 없으면 세 필드 모두 null):

```json
{
  "humanLabel": "DIFF",
  "criteriaDate": "2026-11-13",
  "wrongSide": "SYSTEM"
}
```

- `wrongSide`: `JUDGE` | `SYSTEM` | null(라벨 없음 또는 `UNSURE`).
- 기존 필드·정렬·페이지는 008 계약 그대로.

## 3. 어려운 쌍 판사 성적 — `GET /hard-pair-grades`

파라미터 없음(지금 불일치인 쌍 중 라벨된 것 전체).

응답 `data`:

```json
{
  "labeledPairs": 5,
  "versions": [
    {
      "version": "v2",
      "model": "gpt-4o-mini",
      "judgeRight": { "numerator": 2, "denominator": 4, "percent": 50.0 },
      "systemRight": { "numerator": 2, "denominator": 4, "percent": 50.0 },
      "unsureCount": 1
    }
  ]
}
```

- 라벨이 없으면 `labeledPairs: 0`, `versions: []` — 화면은 "아직 라벨 없음".
- 분모 0 이면 `percent: null`.
- 버전 순서는 판사 버전 목록 순(최신 먼저). 목록에 없는 버전은 뒤, `model: null`.

## 4. 회귀 셋 내보내기 — `GET /disagreement-labels/export`

응답 `data`:

```json
{
  "fileName": "disagreement-labels-2026-11-19.json",
  "items": [ /* data-model.md "회귀 셋 항목" 모양, q 는 1부터 */ ]
}
```

- `items` 가 비면 화면은 파일을 만들지 않고 "내보낼 라벨 없음"을 보인다.
- 같은 쌍은 한 번만(가장 최근 날짜 기준).
- 프론트는 `items` 배열만 파일에 쓴다(최상위가 배열이어야 평가 도구가 읽는다).

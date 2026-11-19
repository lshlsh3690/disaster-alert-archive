# 계약: 클러스터링 관리자 조회 API

공통:
- 경로 접두사 `/api/v1/admin/clustering`, 모든 엔드포인트 `GET`, 읽기 전용.
- 권한: ADMIN 만. 비로그인 401, ADMIN 아닌 회원 403(기존 보안 설정·`@PreAuthorize` 그대로).
- 응답은 공용 `ApiResponse` 로 감싼다. 아래는 `data` 부분.
- 날짜는 `yyyy-MM-dd`(KST 날짜). 비율 객체는 `{ "numerator": int, "denominator": int, "percent": number|null }` — 분모 0 이면 `percent` 가 null.
- 범위 오류(시작 > 끝, 366일 초과, 날짜 형식 오류)는 공용 오류 응답(`ErrorCode`). 시작 > 끝·366일 초과는 `CD400`, 날짜·`kind` 값 형식 오류와 `page`·`size` 범위 오류는 `C001`(둘 다 400). 권한 없음은 `A108`(403).
- 기록 없는 날과 계산할 수 없는 값(`score`·`estimatedCostUsd` 등)은 키를 생략한다. 단 비율 객체(`{numerator, denominator, percent}`)는 분모 0 이면 `percent` 키가 `null` 로 내려간다(비율 객체 안에서는 생략하지 않는다).

## 1. 날짜별 일일 채점

`GET /daily-scores?from=2026-10-19&to=2026-11-17`

```json
{
  "from": "2026-10-19",
  "to": "2026-11-17",
  "days": [
    {
      "date": "2026-11-17",
      "status": "SCORED",
      "incidentCount": 8,
      "pairCount": 6,
      "correctlyMerged": 1, "overMerged": 0, "missed": 0,
      "unsureCount": 0, "formatFailureCount": 0,
      "purity": { "numerator": 1, "denominator": 1, "percent": 100.0 },
      "completeness": { "numerator": 1, "denominator": 1, "percent": 100.0 },
      "score": 100.0,
      "judge": {
        "version": "v1",
        "model": "gpt-4o",
        "sameAgreement": { "numerator": 124, "denominator": 142, "percent": 87.3 },
        "diffAgreement": { "numerator": 77, "denominator": 80, "percent": 96.3 }
      },
      "promptTokens": 9305, "completionTokens": 12,
      "estimatedCostUsd": 0.0234
    },
    { "date": "2026-11-16", "status": "NO_RECORD" }
  ]
}
```

- `status`: `SCORED` | `NO_PAIRS` | `NO_RECORD`. `NO_RECORD` 는 날짜와 상태만.
- `score` 는 계산할 수 없으면 null. `estimatedCostUsd` 는 버전 단가를 모르면 null.
- `judge` 의 일치율은 그날 기록에 남은 당시 값, `model` 은 버전 목록에서.
- 정렬: 날짜 내림차순.

## 2. 불일치 쌍

`GET /disagreements?from=2026-11-17&to=2026-11-17&kind=OVER_MERGED&page=0&size=20`

```json
{
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1,
  "pairs": [
    {
      "date": "2026-11-17",
      "kind": "OVER_MERGED",
      "judgeVerdict": "DIFF",
      "alertA": { "id": 148008, "sentAt": "2026-11-11T22:30:00", "type": "수도", "message": "…" },
      "alertB": { "id": 151505, "sentAt": "2026-11-17T23:00:00", "type": "수도", "message": "…" },
      "eventA": 35579, "eventB": 35579
    }
  ]
}
```

- `kind` 생략 시 두 종류 모두. `size` 1~100(기본 20).
- 알림을 찾을 수 없으면 그 자리 `alertA`/`alertB` 의 `message`·`sentAt`·`type` 이 null(화면 "원문 없음"), `id` 는 그대로.
- 정렬: 날짜 내림차순 → 쌍 id 오름차순.

## 3. 구조 검사

`GET /daily-checks?from=2026-11-11&to=2026-11-17`

```json
{
  "from": "2026-11-11",
  "to": "2026-11-17",
  "days": [
    { "date": "2026-11-17", "recorded": true, "incidentCount": 8, "unassignedCount": 0, "noEmbeddingCount": 0, "attention": false },
    { "date": "2026-11-16", "recorded": false }
  ]
}
```

- `attention` = 미배정 > 0 또는 임베딩 없음 > 0.

### 3-1. 그날의 문제 알림 (행 펼치기)

`GET /daily-checks/2026-11-17/alerts` — 구조 검사 표에서 한 날짜 행을 눌렀을 때 쓴다.

```json
{
  "date": "2026-11-17",
  "alerts": [
    { "id": 9001, "sentAt": "2026-11-17T10:00:00", "type": "화재", "message": "...",
      "unassigned": true, "noEmbedding": false, "eventId": null, "mergeMethod": null },
    { "id": 9002, "sentAt": "2026-11-17T11:00:00", "type": "산불", "message": "...",
      "unassigned": false, "noEmbedding": true, "eventId": 123, "mergeMethod": "REGIONAL_TYPE" }
  ]
}
```

- 대상 = 그날(`created_at` [당일 00:00, 다음날 00:00)) `alert_kind = INCIDENT` 알림 중 이벤트 미배정이거나 임베딩 없음인 것만. 정상 알림·안내(ADVISORY)는 나오지 않는다. 구조 검사 숫자와 같은 조건을 공유한다.
- 정렬: 발송 시각 오름차순 → 알림 id 오름차순.
- `unassigned` = 이벤트 없음, `noEmbedding` = 임베딩 없음(둘 다 true 일 수 있고, 그래도 한 번만 나온다). 이벤트에 붙어 있으면 `eventId`·`mergeMethod` 가 있고, 미배정이면 둘 다 null 이다.
- **숫자는 검사 당시의 저장값이고 목록은 지금 조회한 값이라 다를 수 있다.** 검사 뒤 알림이 배정되거나 임베딩이 채워지면 목록이 숫자보다 짧다. 검사 행이 없는 날도 목록은 조회된다.
- ADMIN 만 호출할 수 있다(다른 엔드포인트와 동일).

## 4. 판사 버전 이력

`GET /judge-versions`

```json
{
  "versions": [
    {
      "version": "v2", "model": "gpt-4o-mini", "measuredOn": "2026-11-18",
      "sameAgreement": { "numerator": 127, "denominator": 141, "percent": 90.1 },
      "diffAgreement": { "numerator": 74, "denominator": 81, "percent": 91.4 },
      "firstScoredDate": "2026-11-18", "lastScoredDate": "2026-11-20", "scoredDays": 3
    },
    {
      "version": "v1", "model": "gpt-4o", "measuredOn": "2026-11-17",
      "sameAgreement": { "numerator": 124, "denominator": 142, "percent": 87.3 },
      "diffAgreement": { "numerator": 77, "denominator": 80, "percent": 96.3 },
      "firstScoredDate": "2026-11-17", "lastScoredDate": "2026-11-17", "scoredDays": 1
    }
  ]
}
```

- 채점 기록이 없는 버전은 `firstScoredDate`·`lastScoredDate` 가 null, `scoredDays` 0.
- 정렬: 버전 목록 순서(최신 먼저).

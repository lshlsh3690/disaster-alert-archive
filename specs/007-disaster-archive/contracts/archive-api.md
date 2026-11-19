# Contract: 기록관 API · 화면 경로

모든 API 는 로그인 없이 GET 으로 호출한다(`SecurityConfig` 에 `GET /api/v1/archive/**` permitAll 추가). 응답은 공용 `ApiResponse<T>`(`success`, `message`, `code`, `data`)로 감싼다. 아래 예시는 `message`·`code` 를 생략하고 `success`·`data` 만 적었다. 이 문서의 경로 규칙(특히 사건 영구 링크)은 로드맵 2단계(보험 증빙)·5단계(언론 위젯)가 그대로 재사용하므로, 바꾸려면 이 문서를 먼저 고친다.

## 1. 연도별 주요 사건 — 스토리 3

```
GET /api/v1/archive/major-events?year={yyyy}
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `year` | 아니오 | 생략 시 `years` 의 첫 값(사건이 있는 가장 최근 연도) |

**200**

```json
{
  "success": true,
  "data": {
    "year": 2026,
    "years": [2026, 2025, 2024],
    "events": [
      {
        "representativeSn": 231845,
        "eventTitle": "청주시 호우 ...",
        "primaryDisasterType": "호우",
        "primaryRegionName": "충청북도 청주시",
        "firstAlertAt": "2026-07-14T09:12:00",
        "lastAlertAt": "2026-07-16T21:40:00",
        "alertCount": 37
      }
    ]
  }
}
```

- `events` 는 정렬된 순서로 최대 10개. 순위 번호·점수 필드는 없다(FR-013).
- `events` 가 빈 배열이면 화면은 "기록된 주요 사건이 없다"를 표시한다.
- `year` 가 숫자가 아니면 기존 바인딩 오류 처리(400)를 따른다.

## 2. 사건 영구 링크 해석 — 스토리 3

```
GET /api/v1/archive/events/{sn}
```

**200 (사건에 묶임)**

```json
{ "success": true, "data": { "sn": 231850, "bound": true, "eventId": 812, "representativeSn": 231845, "alert": null } }
```

**200 (묶이지 않음)**

```json
{ "success": true, "data": { "sn": 231850, "bound": false, "eventId": null, "representativeSn": null,
  "alert": { "alertId": 99812, "sn": 231850, "message": "...", "createdAt": "2026-07-14T09:12:00", "disasterType": "호우", "emergencyLevel": "안전안내" } } }
```

**404**: sn 인 알림이 없음 → `ErrorCode.DISASTER_ALERT_NOT_FOUND` (`D404`).

- `eventId` 는 프론트 서버 컴포넌트가 기존 `GET /api/v1/events/{id}` 를 부르기 위한 내부 값이다. 링크·화면에 노출하지 않는다.

## 3. 기존 사건 상세 응답 필드 추가 — 스토리 3 (FR-004)

```
GET /api/v1/events/{id}   (기존)
```

`EventDetailResponse` 에 `representativeSn: number` 추가. 기존 필드는 바꾸지 않는다. 프론트 `ZEventDetail` 에는 `.optional()` 로 추가해 배포 순서와 무관하게 깨지지 않게 한다.

## 4. 화면 경로

| 경로 | 스토리 | 내용 |
|---|---|---|
| `/archive` | 3 | 연도 선택 + 주요 사건 목록. 각 항목 → `/archive/events/{representativeSn}` |
| `/archive/events/{sn}` | 3 | 사건 기록. 해석 후 기존 사건 상세 화면을 **이 주소 그대로** 그림(리다이렉트 없음). 묶이지 않음이면 안내 + 원문 요약 |
| `/events/{id}` (기존) | 3 | "영구 링크" 표시·복사 추가 → `/archive/events/{representativeSn}` |
| `/archive/alerts/{sn}` | 1 | 원문 기록 + 인용문 (이번 계획의 2단계) |
| `/alerts/{id}` (기존) | 1 | "영구 링크" 표시·복사 추가 → `/archive/alerts/{sn}` |

## 5. 원문 기록 — 스토리 1

```
GET /api/v1/archive/alerts/{sn}
```

**200**

```json
{ "success": true, "data": {
  "sn": 209126, "alertId": 9517,
  "message": "... [경기남부경찰청]",
  "createdAt": "2024-01-16T17:27:43", "modifiedDate": "2024-01-16T00:00:00",
  "disasterType": "기타", "emergencyLevel": "안전안내",
  "originalRegion": "경기도 수원시 장안구,경기도 수원시 권선구",
  "inEvent": true,
  "agency": { "label": "경기남부경찰청", "name": "경기남부경찰청" } } }
```

**404**: sn 인 알림이 없음 → `DISASTER_ALERT_NOT_FOUND`.

- `agency` 는 본문에 발신 기관 표기가 없으면 `null`. `label` 은 본문 표기 그대로(인용문에 쓴다), `name` 은 표기가 가리키는 기관 이름이며 기관 지정 전이면 `null`.
- 사건 ID 는 내보내지 않는다. `inEvent` 가 참이면 화면은 같은 sn 으로 `/archive/events/{sn}` 링크를 만든다(지금 속한 사건으로 해석된다).
- `originalRegion` 은 수집 때 "○○ 전체"의 "전체"와 쉼표 앞뒤 공백만 정리된 값이다. 본문은 원 출처 그대로다.
- `alertId` 는 기존 상세 화면(`/alerts/{id}`)으로 가는 링크용이다.
- 숫자가 아닌 sn 은 이 브랜치에서 500 이 난다(타입 불일치 핸들러는 `feature/clustering-admin-dashboard` 의 `3eed25e8` 에 있음). 화면은 숫자가 아니면 호출 전에 404 를 낸다.

## 6. 스토리 2 에서 추가될 계약 (윤곽만)

| 엔드포인트 | 스토리 | 비고 |
|---|---|---|
| `GET /api/v1/archive/events/{sn}/alerts.csv` | 2 | 사건 포함 원문 전체. 열은 `OpenApiAlertCsvRow` 와 맞추고 `archiveUrl` 열 추가. UTF-8 BOM |

상세 계약은 해당 스토리 착수 때 이 문서에 이어 쓴다.

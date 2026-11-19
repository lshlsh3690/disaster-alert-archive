# Contract: 사건 영구 링크 API · 화면 경로

> 2026-11-19 "기록관" 폐지로 연도별 주요 사건·원문 기록 API 와 화면을 지웠다(spec.md 상단 참고). 남은 계약만 적는다.

모든 API 는 로그인 없이 GET 으로 호출한다(`SecurityConfig` 의 `GET /api/v1/archive/**` permitAll). 응답은 공용 `ApiResponse<T>`(`success`, `message`, `code`, `data`)로 감싼다. 아래 예시는 `success`·`data` 만 적었다. 이 경로 규칙은 뉴스 기사 → 사건 연결(로드맵)이 그대로 재사용하므로, 바꾸려면 이 문서를 먼저 고친다.

## 1. 사건 영구 링크 해석

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
- 숫자가 아닌 sn 은 이 브랜치에서 500 이 난다(타입 불일치 핸들러는 `feature/clustering-admin-dashboard` 의 `3eed25e8`). 화면은 숫자가 아니면 호출 전에 404 를 낸다.

## 2. 기존 사건 상세 응답 필드 추가

```
GET /api/v1/events/{id}   (기존)
```

`EventDetailResponse` 에 `representativeSn: number` 추가. 기존 필드는 바꾸지 않는다. 프론트 `ZEventDetail` 에는 `.nullable().optional()` 로 추가해 배포 순서와 무관하게 깨지지 않게 한다.

## 3. 화면 경로

| 경로 | 내용 |
|---|---|
| `/archive/events/{sn}` | 해석 후 기존 사건 상세 화면을 **이 주소 그대로** 그림(리다이렉트 없음). 묶이지 않음이면 안내 + 원문 요약 |
| `/events/{id}` (기존) | "영구 링크" 표시·복사 → `/archive/events/{representativeSn}` |

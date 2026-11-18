# Data Model: 재난 기록관

**스토리 3(단계 A)은 스키마 변경 없음.** 스토리 1(단계 B)에서 발신 기관 테이블 2개와 `disaster_alert.agency_id` 를 추가한다(아래 "신규 엔티티" 절, research R11).

## 기존 엔티티 (읽기 전용)

### DisasterAlert (`disaster_alert`)

| 필드 | 컬럼 | 기록관에서의 의미 |
|---|---|---|
| `sn` | `sn` (NOT NULL, UNIQUE) | **원문 영구 키.** 원문·사건 영구 링크 모두 이 값으로 만든다 |
| `id` | `disaster_alert_id` | 내부 번호. 링크에 쓰지 않는다 |
| `message` | `message` | 원문 본문 (스토리 1) |
| `createdAt` | `created_at` | 발령 일시. 대표 알림 정렬 1순위 |
| `emergencyLevel` | `emergency_level` | 긴급 단계 (원 출처 값) |
| `disasterType` | `disaster_type` | 재난 유형 |
| `modifiedDate` | `modified_date` | 원 출처 정정 일자 (스토리 1) |
| `originalRegion` | `original_region` | 수신 지역명 원문 (스토리 1) |
| `event` | `event_id` (nullable FK) | 현재 속한 사건. null 이면 "묶이지 않음" |

### DisasterEvent (`disaster_events`)

| 필드 | 컬럼 | 기록관에서의 의미 |
|---|---|---|
| `id` | `id` | **영구하지 않음.** 응답 내부에서 기존 상세 API 호출용으로만 쓰고 링크·화면에 노출하지 않는다 |
| `eventTitle` | `event_title` | 사건명 |
| `primaryDisasterType` | `primary_disaster_type` | 재난 유형 표시 |
| `primaryRegionName` | `primary_region_name` | 대표 지역 |
| `firstAlertAt` | `first_alert_at` | 연도 귀속·정렬 2순위 |
| `lastAlertAt` | `last_alert_at` | 사건 기간 끝 |
| `alertCount` | `alert_count` | 포함 알림 수 (정렬 1순위, 화면에 건수로만 표시) |

## 신규 엔티티 — 스토리 1 (Flyway `V140`)

```text
agency (기관)                 agency_alias (본문 표기)            disaster_alert
─────────────                 ─────────────────────              ──────────────
id          BIGSERIAL PK  ◄── agency_id  BIGINT NOT NULL FK      agency_id BIGINT NULL FK ──► agency.id
name        VARCHAR(100)      label      VARCHAR(100) PK          (idx_disaster_alert_agency)
created_at  TIMESTAMP         created_at TIMESTAMP
```

| 엔티티 | 필드 | 규칙 |
|---|---|---|
| `Agency` | `id`, `name`, `createdAt` | 자동 등록 시 `name` = 처음 본 표기. 이름은 유일하지 않다(정규화 뒤 합쳐질 수 있으므로) |
| `AgencyAlias` | `label`(PK), `agency`(`@ManyToOne` LAZY), `createdAt` | 표기 하나 → 기관 하나. 표기는 본문에서 뽑은 문자열 그대로(공백 포함, 가공 없음) |
| `DisasterAlert` (필드 추가) | `agency`(`@ManyToOne` LAZY, nullable) | 표기가 없으면 null. 표기가 있으면 그 표기가 가리키는 기관 |

- **불변식**: `alert.agency` 가 null 이 아니면, 그 알림 본문에서 뽑은 표기의 `agency_alias.agency_id` 와 같다(정규화로 표기를 다시 연결할 때 알림의 `agency_id` 도 함께 갱신해야 한다).
- **상태 변경**: `DisasterAlert` 는 setter 없이 엔티티 메서드(예: `assignAgency(Agency)`)로만 기관을 바꾼다(팀 컨벤션).
- **표기 추출 함수**: 본문 → `Optional<String>` 순수 함수(헌법 VI). 위치는 `domain/disasteralert/model/SenderLabel`, `AlertPlaceFacts` 가 같은 함수를 호출한다.
- **맥락**: `Agency`·`AgencyAlias` 는 disasteralert 맥락에 둔다. 기관은 문자 본문에서 생겨나 문자와 함께 움직이므로 같은 맥락이고, 그래서 `DisasterAlert → Agency` 객체 참조가 헌법 VII 에 맞는다. 다른 맥락(예: 로드맵 4단계 별점)이 기관을 참조할 때는 `agency_id` 로 한다.
- **백필(V140)**: ① 기존 알림의 서로 다른 표기마다 `agency` 1행 생성 ② 표기 → 기관으로 `agency_alias` 생성 ③ `disaster_alert.agency_id` 갱신. 운영 6만여 건 규모.

## 파생 값

### 대표 알림 (Representative Alert)

- **정의**: 사건 E 에 대해 `event_id = E.id` 인 알림 중 `created_at` 오름차순, 같으면 `sn` 오름차순의 첫 알림.
- **저장**: 하지 않는다. 조회마다 계산한다(research R2).
- **불변식**: 사건이 알림을 1개 이상 가지면 대표 알림은 정확히 1개다. 알림이 0개인 사건은 기록관 응답에서 제외한다(정상 클러스터링에서는 생기지 않음).

### 사건 영구 링크 해석 (sn → 현재 사건)

```text
입력 sn
 ├─ sn 인 알림 없음            → 404 DISASTER_ALERT_NOT_FOUND
 ├─ 알림.event_id 가 null      → bound=false, 알림 요약 반환
 └─ 알림.event_id = E          → bound=true, eventId=E, representativeSn=E 의 대표 알림 sn
```

- 입력 sn 이 E 의 대표 알림이 아니어도 E 로 해석한다(사건이 합쳐진 경우). 화면에 표시하는 영구 링크는 항상 `representativeSn` 으로 만든다.

### 주요 사건 (Major Event)

- **연도 귀속**: `first_alert_at` 이 `[Y-01-01, (Y+1)-01-01)` 안.
- **제외**: 없음 — 유형으로 거르지 않는다(research R3, 2026-11-18 번복).
- **정렬**: `alert_count` 내림차순 → `first_alert_at` 오름차순 → `id` 오름차순(순서 고정용, 비노출).
- **개수**: 상위 10.
- **노출 금지**: 순위 번호, 점수, 등급(FR-013). 목록 순서 자체는 보여주되 "1위" 같은 숫자를 붙이지 않는다.

## 응답 DTO (헌법 II: 응답 형태별 1개 타입, 리스트는 내부 중첩)

| DTO | 용도 | 필드 |
|---|---|---|
| `ArchiveMajorEventsResponse` | 연도별 주요 사건 | `year`, `years: List<Integer>`, `events: List<Item>` — Item: `representativeSn`, `eventTitle`, `primaryDisasterType`, `primaryRegionName`, `firstAlertAt`, `lastAlertAt`, `alertCount` |
| `ArchiveEventResolveResponse` | 사건 영구 링크 해석 | `sn`, `bound`, `eventId`(bound 일 때), `representativeSn`(bound 일 때), `alert`(bound=false 일 때: `alertId`, `sn`, `message`, `createdAt`, `disasterType`, `emergencyLevel`) |
| `EventDetailResponse` (기존, 필드 추가) | 사건 상세 | `representativeSn` 추가 — 기존 사건 상세 화면이 영구 링크를 표시하기 위함(FR-004) |

`ArchiveEventResolveResponse.eventId` 는 프론트 서버 컴포넌트가 기존 상세 API 를 부르기 위한 값이다. 화면이나 링크에는 쓰지 않는다.

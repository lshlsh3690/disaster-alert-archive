# Research: 재난 기록관

Phase 0 결과. 기술 컨텍스트의 미정 사항을 코드로 확인해 결정했다. 근거 줄 번호는 2026-11-18 `develop`(`8641ba92`) 기준.

## R1. 사건 영구 링크를 열 때 리다이렉트할 것인가, 그 자리에서 그릴 것인가

- **결정**: `/archive/events/{sn}` 에서 **그 자리에서 그린다**. 기존 `/events/{id}` 로 리다이렉트하지 않는다.
- **근거**: 리다이렉트하면 주소창이 `/events/123` 으로 바뀌고, 사용자는 주소창을 복사한다. 그 링크는 백필 뒤 다른 사건을 가리킨다(`EventClusteringBackfillTool.java:41` 의 `DELETE FROM disaster_events; SELECT setval(..., 1, false)`). FR-003 의 "사건 번호를 링크에 노출하지 않는다"를 지키려면 영구 링크가 주소창에 남아야 한다.
- **기각한 대안**: 302 리다이렉트(가장 단순하지만 위 이유로 영구성이 깨짐), 클라이언트 `router.replace`(같은 문제).

## R2. 대표 알림을 저장할 것인가, 조회 때 정할 것인가

- **결정**: 조회 때 SQL 로 정한다. 사건에 속한 알림 중 `created_at` 오름차순 → `sn` 오름차순 첫 행.
- **근거**: 사건-알림 매핑은 `disaster_alert.event_id` 한 곳에만 있다(V137, `idx_disaster_alert_event` 인덱스 존재). 대표 알림을 컬럼으로 저장하면 클러스터링이 알림을 붙이거나 옮길 때마다 갱신해야 하고, 그 갱신 지점이 `EventClusteringService` 안의 여러 경로에 흩어진다. 조회 시 계산은 인덱스로 사건당 1회 정렬이라 비용이 작다.
- **기각한 대안**: `disaster_events.representative_sn` 컬럼(갱신 누락 위험, 마이그레이션 필요), 옛 사건→새 사건 매핑 표(명세 가정 절에서 제외).

## R3. "지속형 유형" 목록의 출처

- **결정**: 클러스터링과 **같은 설정 키** `clustering.sustained-types`(`application.yml:156`, 현재 `폭염,한파,대설,강풍,가축질병`)를 `ArchiveService` 에서 `@Value` 로 읽는다.
- **근거**: 기록관이 빼는 이유가 "클러스터링이 이 유형을 지속형으로 이어 붙여 알림 수가 커지기 때문"이므로, 클러스터링의 지속형 목록이 바뀌면 기록관 제외 목록도 같이 바뀌어야 맞다. CLAUDE.md 가 밝힌 대로 `clustering.*` 는 `@ConfigurationProperties` 없이 `@Value` 로 흩어 바인딩하는 것이 이 저장소의 관행이다(`EventClusteringService.java:136`).
- **기각한 대안**: 기록관 전용 키 `archive.excluded-types`(두 목록이 어긋날 수 있음), 상수 하드코딩.

## R4. 주요 사건 쿼리 형태

- **결정**: CTE 로 상위 10개 사건을 먼저 자른 뒤, 그 10개에 대해서만 대표 알림 sn 을 `DISTINCT ON (event_id)` 로 구한다.
  1. `top AS (SELECT ... FROM disaster_events WHERE first_alert_at >= :start AND first_alert_at < :end AND (primary_disaster_type IS NULL OR primary_disaster_type NOT IN (:excluded)) ORDER BY alert_count DESC, first_alert_at ASC, id ASC LIMIT 10)`
  2. `rep AS (SELECT DISTINCT ON (event_id) event_id, sn FROM disaster_alert WHERE event_id IN (SELECT id FROM top) ORDER BY event_id, created_at, sn)`
- **근거**: 대표 sn 을 select 목록의 상관 서브쿼리로 넣으면 플래너가 정렬·LIMIT 전에 모든 후보 행에서 계산할 수 있다. CTE 로 나누면 계산 대상이 10행으로 고정된다. `primary_disaster_type` 이 null 인 사건은 지속형이 아니므로 포함한다(`NOT IN` 은 null 을 걸러 버리므로 명시적으로 살린다).
- **정렬 마지막 키 `id`**: 알림 수와 첫 알림 시각이 모두 같은 경우의 순서를 고정하기 위한 것이다. 응답에는 `id` 를 내보내지 않는다.

## R5. 연도 경계와 시간대

- **결정**: `[year-01-01T00:00, (year+1)-01-01T00:00)` 반열린 구간, 한국 시간 그대로.
- **근거**: `DisasterAlert.createdAt` 은 원 출처 `CRT_DT`(한국 시간 문자열)를 `LocalDateTime` 으로 저장하고, `DisasterEvent.firstAlertAt` 은 그 값에서 파생된다. 시간대 변환이 없는 값이므로 경계도 변환 없이 둔다.

## R6. 연도 선택지 목록

- **결정**: 주요 사건 응답에 `years`(사건이 존재하는 연도 목록, 내림차순)를 함께 내려준다. `SELECT DISTINCT EXTRACT(YEAR FROM first_alert_at)` 한 번.
- **근거**: 프론트가 데이터 시작 연도를 하드코딩하면 `metros.ts` 와 같은 "갱신 누락 지점"이 하나 더 생긴다(CLAUDE.md 법정동 절). 사건 표 크기에서 이 쿼리는 가볍다.

## R7. 응답 포맷

- **결정**: 새 `ArchiveController` 는 헌법 II 대로 `ApiResponse<T>` 로 감싼다.
- **근거**: 기존 `EventController` 는 `ResponseEntity<Page<...>>` 를 그대로 반환해 헌법과 어긋나 있지만(16개 컨트롤러 중 9개만 `ApiResponse` 사용), 새 코드에서 그 불일치를 늘리지 않는다. 기존 `EventController` 를 고치는 일은 이 범위 밖이다.

## R8. 없는 sn·묶이지 않은 알림

- **결정**:
  - 수집된 적 없는 sn → 기존 `ErrorCode.DISASTER_ALERT_NOT_FOUND`(404, `D404`) 재사용. 4xx 이므로 `GlobalExceptionHandler` 규칙상 `warn` 이하로만 남는다(CLAUDE.md 로그 레벨 절).
  - 알림은 있으나 `event_id` 가 null → 200 으로 `bound=false` 와 알림 원문 요약을 내려준다. 오류가 아니라 정상 상태다(비사건 분류 알림은 원래 사건에 묶이지 않는다).

## R9. 사건 기록 화면의 그리기 방식(프론트)

- **결정**: `app/archive/events/[sn]/page.tsx` 가 서버에서 `GET /api/v1/archive/events/{sn}` 으로 사건 ID 를 해석한 뒤, 기존 `fetchEvent(id)` 를 prefetch 하고 기존 `EventDetailClient` 를 그린다. `EventDetailClient` 는 `eventId` prop 을 선택적으로 받도록 바꾸고, 없으면 지금처럼 `useParams` 를 쓴다.
- **근거**: 사건 상세 화면(257줄)을 복제하지 않고 재사용한다. React Query 키 `["event", id, lang]` 도 그대로라 캐시가 공유된다.
- **기각한 대안**: 기록관 전용 사건 화면 신규 작성(스토리 2 의 인용문·CSV 가 붙을 때 다시 검토할 수 있으나 지금은 중복).

## R10. 광역(broadcast) 사건

- **결정**: 이번 규칙에서는 제외하지 않는다(명세 FR-011 은 지속형 유형만 제외).
- **관찰 필요**: 광역 사건도 다지역 알림이 모여 알림 수가 클 수 있다. 구현 뒤 운영 데이터로 연도별 상위 10을 한 번 보고, 광역 사건이 목록을 채우면 사용자에게 보고만 한다(규칙 변경은 사용자 결정).

## R11. 발신 기관을 엔티티로 둘 것인가 (2026-11-18 추가, 스토리 1)

- **결정**: `Agency`(기관)·`AgencyAlias`(본문 표기 → 기관) 엔티티를 새로 두고 `DisasterAlert` 가 `@ManyToOne(LAZY) Agency agency` 로 참조한다. 처음 보는 표기는 자동 등록하고, 정규화(표기 합치기)는 나중에 한다.
- **근거**:
  - 실측(운영 사본, 가짜 20,000건 제외): 62,554건 중 표기 추출 62,545건(본문 끝 60,103 / 맨 앞 2,442), 실패 9건. 표기 709종, 최대 23자.
  - 문자 하나에 기관 하나지만 기관 하나는 문자 여러 건을 보내므로 관계는 1:1 이 아니라 N:1 이다. 이 저장소의 `DisasterAlert → DisasterEvent` 와 같은 지연 로딩 `@ManyToOne` 으로 맞춘다.
  - 표기와 기관이 1:1 이 아니어서(`서울시`/`서울특별시`/`서울특별시청`/`서울틀별시`), 표기를 곧바로 기관 이름으로 쓰면 같은 기관이 여러 엔티티로 갈라진다. 표기 표를 두면 나중에 표기를 다른 기관으로 다시 연결하는 것만으로 합칠 수 있다.
  - 정규화는 기관별 집계가 필요한 로드맵 4단계(문자 별점의 지자체별 리포트)에서 필요해진다. 지금은 인용문 표시용이라 자동 등록만으로 충분하다.
- **기관 종류(시군구/시군구 아님)는 이번에 저장하지 않는다**: 판정에 법정동 이름 사전(`PlaceDirectory`)이 필요하고 동명 구(`동구`·`중구`) 처리까지 얽힌다. 쓰는 곳이 생길 때 추가한다.
- **위치(헌법 VII, 2026-11-18 개정 후 수정)**: 처음에는 `domain/agency` 별도 패키지로 잡았으나, 그러면 `DisasterAlert → Agency` `@ManyToOne` 이 맥락을 넘는다. 기관은 문자 본문에서 생겨나는 개념이라 `domain/disasteralert` 안에 둔다.
- **추출 규칙 공유**: `AlertPlaceFacts.findSender`(private static)의 규칙을 공용 순수 함수 `SenderLabel` 로 옮기고 `AlertPlaceFacts` 도 그 함수를 쓴다. **규칙은 한 글자도 바꾸지 않는다** — `ClusteringJudge` 입력이 이 규칙에서 나오기 때문이다.
- **수집 경로**: `DisasterAlertService.saveData` 의 `toEntity` 매핑 전에 새 알림들의 표기를 모아 한 번에 해석한다(`INSERT ... ON CONFLICT (label) DO NOTHING` 후 조회). 수집은 `@Scheduled` 단일 스레드지만 백필 도구와 겹칠 수 있어 `agency_alias.label` 유일 제약으로 막는다. 해석 실패 시 기관을 비운 채 저장하고 `warn` 을 남긴다(FR-017, 수집을 막지 않음).
- **기존 데이터**: Flyway `V140` 에서 SQL 로 백필한다. SQL 정규식은 Java 규칙과 같은 두 패턴(`\[([^\[\]]+)\]\s*$` → `^\s*\[([^\[\]]+)\]`)이며, 2026-11-18 운영 사본에서 이 SQL 로 위 실측치를 냈다.
- **기각한 대안**: 메서드로만 계산(`DisasterAlert.senderLabel()`, 기관별 조회·집계 불가), 표기 문자열 컬럼(기관 개념 없이 문자열만 중복 저장), 709종을 정리한 사전으로 시작(지금 쓰는 곳 대비 작업이 큼), `@OneToOne`(관계 방향이 틀림).

## 기존 자산 확인 결과

| 항목 | 위치 | 이번에 쓰는 방식 |
|---|---|---|
| `sn` 유일 제약 | `V1` `sn BIGINT NOT NULL UNIQUE`, `DisasterAlert.java:37` | sn → 알림 조회 키 |
| 사건-알림 매핑 + 인덱스 | `V137` `idx_disaster_alert_event` | 대표 알림 계산 |
| 지속형 유형 설정 | `application.yml:156` | 제외 목록 |
| 사건 상세 API | `EventController.java:69`, `EventQueryService.detail` | 해석 후 그대로 재사용 |
| 사건 상세 화면 | `frontend/src/app/events/[id]/EventDetailClient.tsx` | `eventId` prop 추가로 재사용 |
| 공개 GET 경로 | `SecurityConfig.java:54` (`/api/v1/events/**`) | `/api/v1/archive/**` GET 을 같은 방식으로 추가 |
| 404 코드 | `ErrorCode.DISASTER_ALERT_NOT_FOUND` | 없는 sn |

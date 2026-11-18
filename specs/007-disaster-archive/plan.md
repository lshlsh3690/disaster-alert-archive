# 구현 계획: 재난 기록관 (Disaster Archive)

**브랜치**: `feature/disaster-archive` | **날짜**: 2026-11-18 | **명세**: [spec.md](spec.md)

**입력**: `specs/007-disaster-archive/spec.md`의 기능 명세

## 요약

재난문자 원문과 사건을 영구 링크로 인용할 수 있게 한다. 핵심은 **사건 번호가 아니라 원 출처 고유번호(`sn`)로 링크를 만드는 것**이다. 사건 번호는 재클러스터링 백필 때 지워지고 1부터 다시 매겨지기 때문이다(`EventClusteringBackfillTool.java:41`).

사건 영구 링크는 "대표 알림(사건에서 가장 먼저 발령된 알림)의 sn"이며, 열 때 그 알림이 현재 속한 사건을 찾아 **주소를 바꾸지 않고** 기존 사건 상세 화면을 그린다. 스토리 3은 스키마 변경·신규 LLM 호출 없이 조회 쿼리와 화면만 추가한다. 스토리 1에서 발신 기관을 `Agency` 엔티티로 추가한다(research R11, 2026-11-18 결정).

구현 순서는 사용자 결정(2026-11-18)대로 **스토리 3 → 1 → 2**. 이 계획은 스토리 3을 상세화하고 1·2는 윤곽만 둔다.

## 기술 컨텍스트

**언어/버전**: Java 17 + Spring Boot 3.x (백엔드), TypeScript + Next.js App Router (프론트엔드)

**주요 의존성**: Spring Data JPA(네이티브 쿼리), React Query, zod — 신규 라이브러리 없음

**저장소**: PostgreSQL(pgvector 이미지). 스토리 3은 기존 `disaster_alert`·`disaster_events` 읽기 전용(마이그레이션 없음). 스토리 1은 `V140`(기관 테이블 2개 + `disaster_alert.agency_id` + 백필)

**테스트**: JUnit 5 + `@IntegrationTest`(실제 Postgres, `.env.test`) — 쿼리 규칙이 핵심이라 통합 테스트가 주력. 프론트는 jest(node) 대상 로직이 없어 `ui-checker` 화면 점검으로 대신

**대상 플랫폼**: 기존 웹(PWA), 데스크톱·모바일 브라우저

**프로젝트 유형**: web-service (backend + frontend)

**성능 목표**: 주요 사건 목록·영구 링크 해석 모두 체감 즉시(기존 사건 상세와 같은 수준). 대표 알림 계산은 `idx_disaster_alert_event` 인덱스로 사건 10개에만 수행(research R4)

**제약사항**: 사건 번호를 링크·주소창에 노출하지 않음(FR-003), 가공 수치 금지(FR-013), 로그인 불필요(FR-014)

**규모/범위**: 백엔드 신규 패키지 1개(`domain/archive`: controller·service·dto 3~4개 파일) + 기존 리포지토리 쿼리 2~3개 + 기존 DTO 필드 1개. 프론트 신규 라우트 2개 + 기존 컴포넌트 2곳 수정

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 가독성과 단순성 | 통과 | 새 표·매핑 테이블·캐시 없이 조회 2개로 해결. 기존 사건 상세 화면 재사용(R9). 지속형 목록은 기존 설정 재사용(R3) |
| II. 계층형 아키텍처 | 통과 | `ArchiveController → ArchiveService → (event 맥락) EventQueryService / (disasteralert 맥락) DisasterAlertQueryService → 각 맥락 리포지토리`. 응답은 `ApiResponse`(R7), 예외는 `CustomException(ErrorCode.DISASTER_ALERT_NOT_FOUND)`(R8). DTO 는 응답 형태별 1개, 목록은 내부 중첩(data-model.md) |
| III. 검증 가능한 변경 | 통과 | 규칙이 SQL 에 있으므로 `@IntegrationTest` 로 핵심 동작 4개 검증(quickstart 1절). Red-Green 은 `tdd-developer` 2회 호출, Red 후 사용자 확인 대기 |
| IV. 정직한 문서화 | 통과 | 근거를 file:line 으로 남김(research). 광역 사건이 목록을 채울 가능성(R10)과 사건이 쪼개질 때의 한계(spec 예외 상황)를 숨기지 않음 |
| V. 한글 커밋 컨벤션 | 통과 | 커밋은 `commit-message` 스킬로 |
| VI. 도메인 규칙은 순수 클래스로 (1.2.0) | 통과(예외 명시) | 표기 추출(`SenderLabel`)은 순수 함수. 주요 사건 선정(지속형 제외·정렬·상위 10)과 대표 알림 결정은 **목록 전체에 대한 조회 규칙이라 SQL 에 둔다** — 사건 전체를 메모리로 올려 거를 수 없기 때문이며 쿼리 주석에 이 이유를 남긴다 |
| VII. 맥락 경계 (1.2.0) | 통과 | `ArchiveService` 는 리포지토리를 주입하지 않고 `EventQueryService`·`DisasterAlertQueryService` 를 거친다. 기관(`Agency`·`AgencyAlias`)은 문자 본문에서 생겨나 문자와 함께 움직이는 개념이라 **disasteralert 맥락 안**에 두고, 같은 맥락이므로 `DisasterAlert → Agency` 객체 참조를 쓴다 |

**설계 후 재검사(Phase 1 이후)**: 헌법 1.2.0 개정(2026-11-18) 뒤 다시 검사했다. 처음 설계의 두 곳을 고쳤다 — ① `ArchiveService` 가 두 맥락의 리포지토리를 직접 주입하던 것을 각 맥락 서비스 경유로, ② 기관을 별도 `domain/agency` 패키지에 두던 것을 `domain/disasteralert` 안으로(별도 패키지면 `@ManyToOne` 이 맥락을 넘어 VII 위반). 대표 알림 쿼리가 `disaster_alert` 표를 읽는 것은 리포지토리 주입이 아니라 SQL 조인이고, 사건-문자 매핑이 `disaster_alert.event_id` 에 있는 기존 구조(V137) 때문이라 이번에 바꾸지 않는다. 기존 `EventController` 의 `ApiResponse` 미사용은 이번 범위에서 고치지 않는다(R7).

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/007-disaster-archive/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── archive-api.md   # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### 소스 코드

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── domain/archive/                         # 신규
│   ├── controller/ArchiveController.java   # GET /api/v1/archive/major-events, /events/{sn}
│   ├── service/ArchiveService.java         # 연도 구간·제외 유형·해석 분기
│   └── dto/
│       ├── ArchiveMajorEventsResponse.java
│       └── ArchiveEventResolveResponse.java
├── domain/event/repository/DisasterEventRepository.java   # + 주요 사건 CTE 쿼리, 연도 목록, 대표 sn 조회
├── domain/event/service/EventQueryService.java            # + majorEvents(year, excludedTypes), representativeSn(eventId) — archive 가 이 서비스를 거침(VII)
├── domain/disasteralert/repository/DisasterAlertRepository.java  # + findBySn
├── domain/disasteralert/service/DisasterAlertQueryService.java   # 신규, 조회 전용: sn → (alertId, eventId, 원문 요약). DisasterAlertService(874줄)에 더하지 않음
├── domain/event/dto/EventDetailResponse.java              # + representativeSn
├── domain/event/service/EventQueryService.java            # detail 에 대표 sn 채움
├── global/config/SecurityConfig.java                      # + GET /api/v1/archive/** permitAll
│   # ── 스토리 1 (단계 B) ──
├── domain/disasteralert/                                  # 기관은 문자 맥락 안 (VII)
│   ├── model/Agency.java, AgencyAlias.java, SenderLabel.java (표기 추출 순수 함수, VI)
│   ├── repository/AgencyRepository.java, AgencyAliasRepository.java
│   └── service/AgencyResolver.java                        # 표기 목록 → 기관 일괄 해석·자동 등록
├── domain/disasteralert/model/DisasterAlert.java          # + agency (@ManyToOne LAZY, 같은 맥락), assignAgency
├── domain/disasteralert/service/DisasterAlertService.java # saveData 에서 표기 해석
└── domain/event/evaluation/AlertPlaceFacts.java           # findSender → SenderLabel 호출 (규칙 불변)

backend/src/main/resources/db/migration/
└── V140__create_agency_and_backfill.sql                   # 스토리 1

backend/src/test/java/com/disaster/alert/alertapi/domain/archive/
└── ArchiveServiceIntegrationTest.java      # @IntegrationTest, 핵심 동작 4개

frontend/src/
├── api/archiveApi.ts                       # 신규
├── types/archive.ts                        # 신규 zod 스키마
├── types/events.ts                         # ZEventDetail + representativeSn(optional)
├── app/archive/
│   ├── page.tsx                            # 연도별 주요 사건
│   ├── layout.tsx                          # 메타데이터
│   └── events/[sn]/
│       ├── page.tsx                        # 해석 → fetchEvent prefetch → EventDetailClient(eventId)
│       └── layout.tsx                      # generateMetadata (FR-015)
├── app/events/[id]/EventDetailClient.tsx   # eventId prop 선택 수용 + 영구 링크 표시·복사
└── components/PermalinkCopy.tsx            # 신규, 링크 표시 + 복사 + 결과 안내
```

**구조 결정**: 기존 web application 구조(`backend/` + `frontend/`)를 그대로 쓴다. 기록관은 사건·알림 두 도메인을 함께 읽는 조회 전용 기능이라 `domain/event` 에 끼우지 않고 별도 `domain/archive` 를 둔다. 데이터 접근은 기존 리포지토리에 쿼리를 추가해 소유 도메인에 남긴다.

## 단계별 진행

### 단계 A — 스토리 3: 연도별 주요 사건 + 사건 영구 링크 (이번 착수 범위)

| 순서 | 작업 | Red-Green |
|---|---|---|
| A1 | 리포지토리 쿼리(주요 사건 CTE, 연도 목록, 대표 sn, `findBySn`) + `ArchiveService` 뼈대(틀린 기본값 반환) + 통합 테스트 4개 | **RED** — `tdd-developer` 1회차, 커밋 후 멈춤, 사용자 확인 대기 |
| A2 | `ArchiveService` 실제 규칙 | **GREEN** — 사용자가 Red 확인 후 2회차 |
| A3 | `ArchiveController`, DTO, `SecurityConfig`, `EventDetailResponse.representativeSn` | 계층 연결(로직 없음) |
| A4 | 프론트: `archiveApi`, 타입, `/archive`, `/archive/events/[sn]`, `EventDetailClient` prop, `PermalinkCopy` | 프론트도 Red-Green 규칙 적용 대상이나 테스트할 순수 로직이 없어 화면 점검으로 검증 |
| A5 | quickstart 1~4절 실행, `ui-checker` 점검, 로직 100줄 이상 변경이면 `code-reviewer` | — |

Red 단계 테스트는 핵심 동작 4개만 둔다(경계값·사소한 테스트 제외, 메모리 규칙).

### 단계 B — 스토리 1: 발신 기관 + 원문 영구 링크 + 인용문

| 순서 | 작업 | Red-Green |
|---|---|---|
| B1 | 표기 추출 순수 함수를 `domain/disasteralert/model/SenderLabel` 로 옮기고 `AlertPlaceFacts` 가 호출하도록 변경. **규칙 불변** — 기존 `AlertPlaceFactsTest` 가 그대로 통과해야 함 | 리팩터링(동작 불변, 기존 테스트로 검증) |
| B2 | `Agency`·`AgencyAlias` 엔티티, 리포지토리, `V140` 마이그레이션(테이블 + 백필), `DisasterAlert.agency` 필드·`assignAgency` | **RED**: 표기 해석(있는 표기 → 기존 기관 / 처음 보는 표기 → 자동 등록 / 표기 없음 → null) 통합 테스트. 사용자 확인 후 GREEN |
| B3 | `DisasterAlertService.saveData` 에서 `toEntity` 전에 표기 일괄 해석, 실패 시 기관 null + `warn` | B2 GREEN 에 포함 |
| B4 | `GET /api/v1/archive/alerts/{sn}`, `/archive/alerts/[sn]` 화면, `/alerts/{id}` 에 영구 링크 | 계층 연결 |
| B5 | 인용문 조립(프론트, FR-006 형식·"외 N곳" 축약) | 순수 함수라 프론트 단위 테스트(RED → 확인 → GREEN) |

`V140` 은 운영 배포 때 6만여 건을 백필한다. 운영 반영은 사용자가 배포를 결정할 때만 일어난다(운영에 영향을 주는 명령은 Claude 가 직접 실행하지 않음).

### 단계 C — 스토리 2: 사건 인용문 + CSV (윤곽)

사건 인용문(FR-007, "자동 분류 결과" 문구 포함), `GET /api/v1/archive/events/{sn}/alerts.csv` — `OpenApiCsvWriter` 재사용 검토, UTF-8 BOM, `archiveUrl` 열.

## 위험과 관찰 항목

| 항목 | 내용 | 대응 |
|---|---|---|
| 광역 사건이 목록 상위 차지 | 지속형만 빼므로 다지역 광역 사건이 남을 수 있음(R10) | 구현 후 운영 사본으로 연도별 상위 10을 보고. 규칙 변경은 사용자 결정 |
| 사건이 쪼개질 때 | 대표 알림 쪽 사건만 보임 | 명세에서 수용한 한계 |
| 로컬 DB 에 사건 없음 | 재클러스터링 백필은 LLM 비용 발생 | 운영 사본 덤프 사용, 백필은 사용자 승인 후 |
| `ZEventDetail` 필드 추가 | 백엔드보다 프론트가 먼저 배포되면 파싱 실패 가능 | `.optional()` 로 추가 |
| 표기 추출 규칙 공유 | `AlertPlaceFacts` 는 일일 채점 판사 입력을 만든다. 규칙이 바뀌면 판사 입력이 바뀜 | B1 은 동작 불변 리팩터링, 기존 `AlertPlaceFactsTest` 통과로 확인 |
| 같은 기관의 여러 표기 | 정규화 전까지 `서울시`·`서울특별시`가 다른 기관으로 보임 | 인용문은 표기(`label`)를 쓰므로 영향 없음. 기관별 집계는 로드맵 4단계에서 정규화와 함께 |

## 복잡도 추적

헌법 위반 없음 — 작성하지 않는다.

# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

Disaster Alert Archive(재난 안전 문자 아카이브) — 한국 정부의 재난문자 공공데이터를 수집하고, 관련 알림들을 "이벤트"로 클러스터링하며, 지역 단위 위험도 점수를 계산하고, 관심지역 기반으로 사용자에게 FCM 푸시 알림을 보내는 서비스입니다. 백엔드는 Spring Boot, 프론트엔드는 Next.js를 사용합니다. 대부분의 식별자, 주석, 커밋 메시지는 한국어로 작성되어 있습니다.

## 커맨드

### 백엔드 (`backend/`)

```bash
./gradlew compileJava        # 컴파일만 (빠른 확인용)
./gradlew build               # 전체 빌드 + 테스트
./gradlew test                 # 전체 테스트 실행
./gradlew test --tests "com.disaster.alert.alertapi.domain.auth.service.AuthServiceTest"   # 단일 테스트 클래스
./gradlew test --tests "*.AuthServiceTest.someMethod"                                       # 단일 테스트 메서드
```

테스트는 `@SpringBootTest` + `@ActiveProfiles("test")`를 사용하며 `backend/.env.test`를 통해 실제(테스트용) Postgres에 접속합니다 — 슬라이스/목킹된 단위 테스트가 아니므로, dev docker-compose와 동일한 구성의 DB에 접속 가능해야 합니다.

QueryDSL Q-클래스는 컴파일 시 `backend/src/main/generated`에 생성됩니다 — 리포지토리 쿼리 메서드가 인식되지 않으면 `./gradlew compileJava`로 재생성하세요.

### 프론트엔드 (`frontend/`)

```bash
npm run dev      # next dev --turbopack
npm run build    # next build --webpack (빌드는 turbopack이 아닌 webpack 사용)
npm run start
npm run lint
```

### 로컬 인프라

```bash
docker compose -f docker-compose.dev.yml up postgres redis   # Postgres(pgvector/pgvector:pg15) + Redis만, --profile docker 불필요
```
`docker-compose.dev.yml`의 `frontend`/`backend` 서비스는 `docker` 프로필에 묶여 있습니다 — 로컬 개발 시에는 Postgres/Redis만 compose로 띄우고 백엔드/프론트엔드는 직접 실행하세요 (`./gradlew bootRun`, `npm run dev`).

스키마는 Flyway로 관리합니다 (`backend/src/main/resources/db/migration/V*.sql`, `ddl-auto: validate`) — 이미 적용된 마이그레이션은 절대 수정하지 말고 새 `V{n}__description.sql`을 추가하세요. 법정동 번역 시딩과 여러 분기에 걸친 날씨 이력 백필용 시드 마이그레이션이 다수 존재합니다.

## 아키텍처

### 백엔드 도메인 구조

`backend/src/main/java/com/disaster/alert/alertapi/`
- `domain/*` — bounded context별 패키지 (`disasteralert`, `event`, `risk`, `weather`, `notification`, `member`, `auth`, `legaldistrict`, `missingperson`, `community`, `comment`, `useralert`, `openapi`). 각각 `controller → service → repository` 구조를 따르며, `model/`(JPA 엔티티)과 `dto/`(엔드포인트별 응답 DTO 1개 타입, 리스트는 별도 타입이 아니라 내부에 중첩)로 구성됩니다.
- `global/` — 공통 관심사: `security/jwt`(JWT 인증 필터/프로바이더), `exception`(`GlobalExceptionHandler` + `CustomException`/`ErrorCode` 패턴 — 항상 ErrorCode를 통해 예외를 던지고 임의로 에러 바디를 만들지 말 것), `dto`(모든 컨트롤러가 사용하는 `ApiResponse`/`ApiErrorResponse` 응답 포맷), `translation`(DeepL 기반), `redis`, `config`.
- `scheduler/` 및 도메인별 `*/scheduler/` 패키지 — `@Scheduled` 작업들 (재난문자 수집, 날씨 수집/집계, 위험도 감쇠, fragment-merge). 백그라운드 파이프라인의 진입점이므로 "왜 X가 실행되지 않았는지" 디버깅할 때 가장 먼저 확인할 곳입니다.
- `api/DisasterOpenApiClient.java` — 재난문자 공공데이터포털 API 클라이언트.

인증: httpOnly 쿠키 기반 JWT access+refresh 토큰 (`application.yml`의 `cookie.secure`/`cookie.domain` 참고), Google/Kakao/Naver OAuth2 로그인은 `domain/auth/oauth`에 직접 구현되어 있습니다 (`build.gradle`의 Spring `oauth2-client` 스타터 의존성은 주석 처리되어 있으며, 각 프로바이더는 수작업으로 구현됨).

### 이벤트 클러스터링 파이프라인 (가장 복잡한 서브시스템)

원본 알림(`disasteralert`)은 임베딩 기반 클러스터링을 통해 `DisasterEvent`로 그룹핑됩니다. 관련 로직은 `domain/event/service/`에 모여 있습니다.
- `EventClusteringService` — 메인 클러스터링 진입점. 지역(같은 시군구)으로 후보를 하드 필터링하고, 시간 윈도우 내에서 코사인 유사도가 `clustering.similarity-threshold`(0.85) 이상이어야 기존 이벤트에 병합됩니다.
- `EventCrossRegionService` — 실종자, 탈출 동물처럼 지역을 넘나드는 알림을 위한 별도 패스. `mover-keywords`/`animal-keywords`로 게이트하며 LLM이 판정합니다.
- `EventFragmentMergeService` — 30분 주기 스케줄러로 동작하는 정합화 패스. 작은 "파편" 이벤트를 같은 유형/시도의 더 큰 이벤트로 다중 메시지 LLM 비교를 통해 흡수합니다 (동일한 실제 사건이 서로 다른 법정동 레벨로 신고된 경우, 예: 시 단위 vs 구 단위 코드, 를 처리).
- `EventLLMDecisionService` — 위 서비스들이 borderline 병합 판정에 공용으로 사용하는 LLM 판정 호출부 (Spring AI + `gpt-4o-mini`). 호출당 비용이 들기 때문에 config 플래그(`llm-fallback.enabled`, `cross-region.enabled`) 뒤에 감춰져 있습니다.
- `ClusteringProperties` — `application.yml`의 방대한 `clustering.*` 블록을 바인딩합니다. 각 임계값/키워드 목록이 존재하는 이유(blob 방지, 일반 계절성 안전안내 문자의 cooldown 억제, 산불/지진 등 인접 시군구 BFS 확산 hop)가 한국어 주석으로 상세히 설명되어 있습니다. **클러스터링 임계값을 변경하기 전에 반드시 그 주석들을 먼저 읽으세요** — 대부분의 값은 임의가 아니라 실제 사건 데이터로 튜닝된 값입니다.
- `EventClusteringBackfillTool` — 현재 클러스터링 설정으로 과거 알림을 재처리하는 도구. 임계값 튜닝 시 사용.
- `DisasterCooldown` — 지역/유형별 쿨다운으로, 진행 중인 이벤트에 대한 반복 알림이 중복 알림을 만들지 않도록 함. 계절성 안전안내 문자(예: 봄철 산불 예방 안내) 같은 일반 안내성 메시지는 쿨다운을 갱신하면 안 됨 — `incident-specific-check` 설정 참고.

대부분의 클러스터링 기능은 환경변수 기반 플래그(`CLUSTERING_ENABLED`, `LLM_FALLBACK_ENABLED`, `CROSS_REGION_ENABLED`, `FRAGMENT_MERGE_ENABLED`, `INCIDENT_SPECIFIC_CHECK_ENABLED`)로 기본값이 `false`입니다 — 특정 코드 경로가 실제로 실행된다고 가정하기 전에 대상 환경에서 어떤 플래그가 켜져 있는지 확인하세요.

### 위험도 계산 (Risk scoring)

`domain/risk/`는 클러스터링된 이벤트로부터 지역별 일일 위험도(`RegionRiskDaily`)를 계산하며, `RiskDecayScheduler`가 시간에 따라 점수를 감쇠시키고 `LlmRiskProfiler`가 기존 35종 프로파일에 없는 재난 유형을 처리합니다. 클러스터링 결과는 Spring 애플리케이션 이벤트인 `AlertClusteredEvent`를 `ClusteringEventListener`가 구독하는 방식으로 전달받습니다 — 위험도 계산은 클러스터링 로직과 분리되어 있습니다.

### 알림 (FCM)

`domain/notification/` — `AlertNotificationService`는 알림의 법정동 코드로부터 알릴 대상 회원/게스트를 찾습니다 (특정 구가 아닌 시도 전체를 등록한 사용자를 위해 파생된 시도 레벨 코드도 포함). `FcmSendService`는 Firebase Admin SDK로 발송합니다. **FCM 메시지는 반드시 data-only여야 합니다** (`.setNotification()` 사용 금지) — `notification` 필드가 있으면 Chrome이 자동 표시와 백그라운드 핸들러 실행을 동시에 하여 알림이 중복 표시됩니다 (과거에 수정된 버그이므로 이 필드를 다시 추가하지 마세요). 프론트엔드 서비스워커(`frontend/public/firebase-messaging-sw.js`)의 `onBackgroundMessage` 핸들러는 반드시 `showNotification()`을 `await`해야 합니다 — 그렇지 않으면 알림이 표시되기 전에 SW가 종료될 수 있습니다.

### 법정동

한국 행정구역 코드가 알림, 관심지역, 위험도 등 지역 매칭 전반을 구동합니다. 코드 구조: 1-2번째 자리 = 시도, 3-5번째 자리 = 시군구, 6-8번째 자리 = 읍면동, 9-10번째 자리 = 리. 시도 전체 선택("전체")은 시도 코드 뒤를 0으로 채운 형태로 저장됩니다 (예: 광주광역시 전체 = `2900000000`). 지역 매칭 로직은 알림의 시군구 코드를 단순 일치시키는 것이 아니라 이 시도 레벨 코드를 파생해서 함께 확인해야 합니다.

### 프론트엔드 구조

`frontend/src/`
- `api/*.ts` — 백엔드 도메인별 파일 하나씩, 얇은 axios 래퍼. `api/axios.ts`에 401 → `/auth/reissue` 리프레시 인터셉터가 포함된 공용 인스턴스가 있습니다 (리프레시 중 들어온 동시 요청은 큐잉했다가 각각 1회만 재시도하며, reissue/login 요청 자체는 재시도하지 않음).
- `lib/queries/`, `lib/mutations/` — `api/*` 위에 구축된 React Query 훅.
- `store/` — Zustand 스토어 (`authStore`, 비로그인 사용자용 `guestFavoriteRegionsStore`, `languageStore`, `signupStore`).
- `app/` — Next.js App Router 페이지 (alerts, community, dashboard, events, missing-persons, stats, user settings/regions 등).
- PWA 지원(`@ducanh2912/next-pwa`)이며 푸시 알림을 위한 Firebase Messaging 서비스워커가 있습니다.

## 팀 컨벤션 (`prompts/PROMPTS.md` 기반)

- 백엔드: controller → service → repository 계층 구조. DTO는 응답 형태별로 1개 타입, 리스트는 별도 타입이 아니라 내부에 중첩. 모든 응답은 공용 `ApiResponse`/`ApiErrorResponse` 포맷 사용. 요청 DTO는 `@Valid`로 검증. 도메인 상태 변경은 서비스에서 setter가 아닌 엔티티 메서드를 통해서만 수행. 예외는 `CustomException` + `ErrorCode`로 던지고 임의 방식 금지.
- 메서드는 가독성을 위해 대략 30~50줄 이내로 유지하고, 사소한 필요로 새 라이브러리를 들이지 않기.
- 프론트엔드: React Query 키는 tuple로 구성. 특별한 필요가 없다면 낙관적 업데이트는 지양. DTO 필드명은 백엔드와 정확히 일치시키기.

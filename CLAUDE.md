# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

Disaster Alert Archive(재난 안전 문자 아카이브) — 한국 정부의 재난문자 공공데이터를 수집하고, 관련 알림들을 "이벤트"로 클러스터링하며, 지역 단위 위험도 점수를 계산하고, 관심지역 기반으로 사용자에게 FCM 푸시 알림을 보내는 서비스입니다. 백엔드는 Spring Boot, 프론트엔드는 Next.js를 사용합니다. 대부분의 식별자, 주석, 커밋 메시지는 한국어로 작성되어 있습니다.

## 커맨드

### 백엔드 (`backend/`)

```bash
./gradlew compileJava        # 컴파일만 (빠른 확인용)
./gradlew compileTestJava     # 테스트 소스 컴파일만
./gradlew build               # 전체 빌드 + 테스트
./gradlew test                 # 전체 테스트 실행 (realApi 태그 제외)
./gradlew test --tests "com.disaster.alert.alertapi.domain.auth.service.AuthServiceTest"   # 단일 테스트 클래스
./gradlew test --tests "*.AuthServiceTest.someMethod"                                       # 단일 테스트 메서드
./gradlew realApiTest          # 실제 OpenAI 를 호출하는 테스트만 (@Tag("realApi"))
```

`@Tag("realApi")` 가 붙은 테스트(현재 `OpenAiTranslationClientRealApiTest`)는 실제 OpenAI API 를 호출하므로 `OPENAI_API_KEY` 가 필요하고 실행마다 요금이 발생하며 응답이 완전히 결정적이지 않습니다. `build.gradle`에서 기본 `test` 태스크는 이 태그를 제외하고, 별도 `realApiTest` 태스크로만 실행하도록 분리되어 있습니다 — 프롬프트 규칙을 바꿨을 때 수동으로 돌려 확인하는 용도입니다.

DB/Spring 컨텍스트에 의존하는 통합 테스트는 `@SpringBootTest` + `@ActiveProfiles("test")` + `backend/.env.test` 로딩을 한 번에 묶은 `@IntegrationTest`(`global/testsupport/IntegrationTest.java`, `DotenvExtension` 포함)를 붙여 작성합니다 — 슬라이스/목킹된 단위 테스트가 아니므로, dev docker-compose와 동일한 구성의 DB에 접속 가능해야 합니다. 반면 외부 의존성이 없는 순수 로직 클래스(`domain/event/model`의 `FireAlertClassifier`·`DisasterCooldown`·`MissingPersonIdentity`·`AnimalIdentity`, `domain/risk/service`의 `IntensityExtractor` 같은 정적 유틸/판정 클래스)는 `@SpringBootTest` 없이 순수 JUnit 단위 테스트로 작성합니다 — DB 연결이나 Spring 컨텍스트 부트스트랩이 필요 없어 훨씬 빠릅니다.

QueryDSL Q-클래스는 컴파일 시 `backend/src/main/generated`에 생성됩니다 — 리포지토리 쿼리 메서드가 인식되지 않으면 `./gradlew compileJava`로 재생성하세요.

### 프론트엔드 (`frontend/`)

```bash
npm run dev      # next dev --turbopack
npm run build    # next build --webpack (빌드는 turbopack이 아닌 webpack 사용)
npm run start
npm run lint
npm test         # jest (testEnvironment: node). 현재 src/api/alertApi.test.ts 만 존재
```

### 로컬 인프라

```bash
docker compose -f docker-compose.dev.yml up postgres redis   # Postgres(pgvector/pgvector:pg15) + Redis만, --profile docker 불필요
```
`docker-compose.dev.yml`의 `frontend`/`backend` 서비스는 `docker` 프로필에 묶여 있습니다 — 로컬 개발 시에는 Postgres/Redis만 compose로 띄우고 백엔드/프론트엔드는 직접 실행하세요 (`./gradlew bootRun`, `npm run dev`).

스키마는 Flyway로 관리합니다 (`backend/src/main/resources/db/migration/V*.sql`, `ddl-auto: validate`) — 이미 적용된 마이그레이션은 절대 수정하지 말고 새 `V{n}__description.sql`을 추가하세요. 법정동 번역 시딩과 여러 분기에 걸친 날씨 이력 백필용 시드 마이그레이션이 다수 존재합니다. `flyway.out-of-order: true`라 번호가 어긋난 미적용 마이그레이션도 실행됩니다.

루트 `.env.example`은 백엔드용 템플릿이며 2026-08-10 기준 `application.yml`·`application-prod.yml`의 `${...}` 참조와 정확히 1:1로 맞춰져 있습니다 (프론트엔드는 `frontend/.env.local.example`). 환경변수를 추가/제거할 때 이 템플릿도 함께 갱신하고, 판단 기준은 항상 `application.yml`의 실제 참조로 삼으세요. Spring AI(임베딩·LLM 판정·번역)는 `OPENAI_API_KEY` 하나를 공유합니다. FCM 발송용 서비스 계정 키만은 환경변수가 아니라 클래스패스 파일(`backend/src/main/resources/firebase-service-account.json`)로 읽습니다 — `global/config/FirebaseConfig` 참고.

## 아키텍처

### 백엔드 도메인 구조

`backend/src/main/java/com/disaster/alert/alertapi/`
- `domain/*` — bounded context별 패키지 (`disasteralert`, `event`, `risk`, `weather`, `notification`, `member`, `auth`, `legaldistrict`, `community`, `comment`, `useralert`, `openapi`, `common`). 각각 `controller → service → repository` 구조를 따르며, `model/`(JPA 엔티티)과 `dto/`(엔드포인트별 응답 DTO 1개 타입, 리스트는 별도 타입이 아니라 내부에 중첩)로 구성됩니다. 모든 컨트롤러는 `/api/v1/*` 아래에 매핑됩니다. **실종자 전용 도메인 패키지는 없습니다** — 실종자는 재난문자에서 파생되는 개념이라 `domain/event/model/MissingPersonIdentity`(동일 실종 사건 판정)와 `domain/disasteralert` 조회 필터로 구현되어 있습니다.
- `global/` — 공통 관심사: `security/jwt`(JWT 인증 필터/프로바이더), `exception`(`GlobalExceptionHandler` + `CustomException`/`ErrorCode` 패턴 — 항상 ErrorCode를 통해 예외를 던지고 임의로 에러 바디를 만들지 말 것), `dto`(모든 컨트롤러가 사용하는 `ApiResponse`/`ApiErrorResponse` 응답 포맷), `translation`(OpenAI 기반 — DeepL Free는 계정당 평생 누적 쿼터라 예비 키까지 소진돼 2026-08-09 교체), `logtrace`(김영한 LogTrace 스타일 AOP 호출 로깅 — controller/service 계층에 트랜잭션 ID + 들여쓰기 로그 자동 부착), `service`(`EmailService`, `LegalDistrictCache`), `controller/AdminController`, `util/CookieUtil`, `redis`, `config`.
- `scheduler/DisasterFetchScheduler` 및 도메인별 `*/scheduler/` 패키지(`weather/scheduler/WeatherCollectScheduler`·`WeatherDailySummaryScheduler`, `risk/scheduler/RiskDecayScheduler`) — `@Scheduled` 작업들. 백그라운드 파이프라인의 진입점이므로 "왜 X가 실행되지 않았는지" 디버깅할 때 가장 먼저 확인할 곳입니다.
- `api/DisasterOpenApiClient.java` — 재난문자 공공데이터포털 API 클라이언트.
- `*/tool/*BackfillTool` — 과거 데이터를 현재 로직으로 재처리하는 수동 실행 도구 (`EventClusteringBackfillTool`, `RiskBackfillTool`, `DisasterAlertRegionBackfillTool`).

**외부 에러 트래킹 서비스는 쓰지 않습니다** — 2026-08-12에 Sentry(`sentry-spring-boot-starter-jakarta` + `sentry-logback`)를 제거했습니다. 장애 인지 수단은 로그뿐이며, 자동 알림 경로가 없다는 뜻입니다. 따라서 **로그 레벨이 사실상 유일한 신호 구분자**입니다: 사람이 조치해야 하는 것만 `log.error`로 남기고, 정상 흐름에서 늘 발생하는 것(4xx 비즈니스 예외, 죽은 FCM 토큰 등)은 `warn` 이하로 둡니다. 이 구분이 무너지면 `error`가 노이즈에 묻혀 진짜 장애를 못 찾습니다 — `GlobalExceptionHandler`(5xx만 error), `ThreadLocalLogTrace`(전부 info), `FcmSendService.logDeadToken`(죽은 토큰은 warn)이 그 예입니다.

인증: httpOnly 쿠키 기반 JWT access+refresh 토큰 (`application.yml`의 `cookie.secure`/`cookie.domain` 참고), Google/Kakao/Naver OAuth2 로그인은 `domain/auth/oauth`에 직접 구현되어 있습니다 (`build.gradle`의 Spring `oauth2-client` 스타터 의존성은 주석 처리되어 있으며, 각 프로바이더는 수작업으로 구현됨).

### 이벤트 클러스터링 파이프라인 (가장 복잡한 서브시스템)

원본 알림(`disasteralert`)은 임베딩 기반 클러스터링을 통해 `DisasterEvent`로 그룹핑됩니다. 관련 로직은 `domain/event/service/`에 모여 있습니다.
- `EventClusteringService` — 메인 클러스터링 진입점. 지역(같은 시군구)으로 후보를 하드 필터링하고, 시간 윈도우 내에서 코사인 유사도가 `clustering.similarity-threshold`(0.85) 이상이어야 기존 이벤트에 병합됩니다.
- `EventCrossRegionService` — 실종자, 탈출 동물처럼 지역을 넘나드는 알림을 위한 별도 패스. `mover-keywords`/`animal-keywords`로 게이트하며 LLM이 판정합니다.
- `EventLLMDecisionService` — 위 서비스들이 borderline 병합 판정에 공용으로 사용하는 LLM 판정 호출부 (Spring AI + `gpt-4o-mini`). 호출당 비용이 들기 때문에 config 플래그(`llm-fallback.enabled`, `cross-region.enabled`) 뒤에 감춰져 있습니다.
- `application.yml`의 `clustering.*` 블록 값들은 별도 `@ConfigurationProperties` 클래스 없이 `EventClusteringService`/`EventCrossRegionService`에 개별 `@Value` 필드로 흩어져 바인딩됩니다. 각 임계값/키워드 목록이 존재하는 이유(blob 방지, 유형별 머지 윈도우 등)가 한국어 주석으로 설명되어 있습니다. **클러스터링 임계값을 변경하기 전에 반드시 그 주석들을 먼저 읽으세요** — 대부분의 값은 임의가 아니라 실제 사건 데이터로 튜닝된 값입니다. (인접 시군구 BFS 확산 전파는 클러스터링이 아니라 `domain/risk`의 `RiskCalculationService.propagateEffective()`에 있습니다 — 아래 위험도 계산 절 참고.)
- `EventClusteringBackfillTool` — 현재 클러스터링 설정으로 과거 알림을 재처리하는 도구. 임계값 튜닝 시 사용.
- `DisasterCooldown` — 지역/유형별 쿨다운으로, 진행 중인 이벤트에 대한 반복 알림이 중복 알림을 만들지 않도록 함. 계절성 안전안내 문자(예: 봄철 산불 예방 안내) 같은 일반 안내성 메시지는 `clustering.advisory-split-types`(현재 `산불`만 등록)에 해당하면 실제 사건 이벤트와 별도의 "안내성"(advisory) 이벤트로 분리 생성되어, 실제 사건의 쿨다운을 갱신하지 않음 (`EventClusteringService.clusterFireAdvisory`/`isAdvisorySplitType`).

대부분의 클러스터링 기능은 환경변수 기반 플래그(`CLUSTERING_ENABLED`, `LLM_FALLBACK_ENABLED`, `CROSS_REGION_ENABLED`)로 기본값이 `false`입니다 — 특정 코드 경로가 실제로 실행된다고 가정하기 전에 대상 환경에서 어떤 플래그가 켜져 있는지 확인하세요.

이 서브시스템의 as-built 상세 동작(37개 기능 요구사항, file:line 근거 포함)은 `specs/001-event-clustering-pipeline/spec.md`·`plan.md` 참고.

### 위험도 계산 (Risk scoring)

`domain/risk/`는 클러스터링된 이벤트로부터 지역별 일일 위험도(`RegionRiskDaily`)를 계산하며, `RiskDecayScheduler`가 시간에 따라 점수를 감쇠시키고 `LlmRiskProfiler`가 기존 35종 프로파일에 없는 재난 유형을 처리합니다 (클러스터링 쪽 LLM 폴백과 달리 config 플래그로 게이트되어 있지 않아, 미등록 유형을 만나면 항상 호출됩니다). 클러스터링 결과는 Spring 애플리케이션 이벤트인 `AlertClusteredEvent`를 `ClusteringEventListener`가 구독하는 방식으로 전달받습니다 — 위험도 계산은 클러스터링 로직과 분리되어 있습니다. 인접 시군구로의 위험도 확산은 `RiskCalculationService.propagateEffective()`가 `RegionAdjacencyRepository` 인접 그래프를 다중소스 BFS(진원별 홉 감쇠, MAX 결합)로 전파하는 방식으로 구현되어 있습니다.

이 서브시스템의 as-built 상세 동작은 `specs/002-risk-scoring/spec.md`·`plan.md` 참고.

### 알림 (FCM)

`domain/notification/` — `AlertNotificationService`는 알림의 법정동 코드로부터 알릴 대상 회원/게스트를 찾습니다 (특정 구가 아닌 시도 전체를 등록한 사용자를 위해 파생된 시도 레벨 코드도 포함). `FcmSendService`는 Firebase Admin SDK로 발송합니다. **FCM 메시지는 반드시 data-only여야 합니다** (`.setNotification()` 사용 금지) — `notification` 필드가 있으면 Chrome이 자동 표시와 백그라운드 핸들러 실행을 동시에 하여 알림이 중복 표시됩니다 (과거에 수정된 버그이므로 이 필드를 다시 추가하지 마세요). 프론트엔드 서비스워커(`frontend/public/firebase-messaging-sw.js`)는 Firebase JS SDK의 `onBackgroundMessage()`가 아니라 표준 Push API의 `push` 이벤트를 직접 파싱해 처리합니다 (SDK의 push 이벤트 라우팅 이슈로 알림을 못 받거나 씹는 문제가 있어 재작성됨) — 반드시 `event.waitUntil()`로 감싸 비동기 처리(파싱+`showNotification()`)가 끝날 때까지 SW가 살아있게 해야 합니다. (`fcm_token.token`도 전역 UNIQUE 제약이 걸려 있어, 토큰 UPSERT 시 다른 행과의 충돌을 먼저 정리해야 합니다 — `FcmTokenService.registerToken` 참고. (memberId, alertId) 단위 중복 발송 방지는 2026-08-03 제거되어 현재 존재하지 않습니다.)

이 서브시스템의 as-built 상세 동작은 `specs/003-fcm-notification/spec.md`·`plan.md` 참고.

### 법정동

한국 행정구역 코드가 알림, 관심지역, 위험도 등 지역 매칭 전반을 구동합니다. 코드 구조: 1-2번째 자리 = 시도, 3-5번째 자리 = 시군구, 6-8번째 자리 = 읍면동, 9-10번째 자리 = 리. 시도 전체 선택("전체")은 시도 코드 뒤를 0으로 채운 형태로 저장됩니다 (예: 전남광주통합특별시 전체 = `1200000000`). 지역 매칭 로직은 알림의 시군구 코드를 단순 일치시키는 것이 아니라 이 시도 레벨 코드를 파생해서 함께 확인해야 합니다. 이 시도 코드 파생(`substring(0,2)`)은 `AlertNotificationService`/`EventClusteringService`/`DisasterAlertRepositoryImpl`에 각각 별도로 구현되어 있어 공용 헬퍼가 없습니다 — 새 시도 코드가 추가되면 프론트엔드의 하드코딩된 시도 매핑(`frontend/src/ui/metros.ts`)처럼 갱신이 누락되기 쉬우니 지역 매칭 관련 변경 시 이 중복 지점들을 함께 확인하세요.

2026-07-01 광주광역시(`29xx`)·전라남도(`46xx`)가 **전남광주통합특별시(`12xx`)로 통합**되며 법정동코드가 전면 재발급되었습니다 (V108 신규 코드 시딩, V111 기존 데이터 이관). 옛 `29`/`46` 시도 코드는 더 이상 조회되지 않으므로 새 코드를 기준으로 작업하세요 — 이관 전 수집분 보정용으로 `DisasterAlertRegionBackfillTool`이 있습니다.

이 서브시스템의 as-built 상세 동작은 `specs/004-legal-district-matching/spec.md`·`plan.md` 참고.

### 번역 파이프라인

`global/translation/` — 재난문자 본문/유형과 이벤트 제목을 KO → EN/JA/ZH 로 번역합니다. 수집 스케줄러가 새 알림을 저장하면 `translateAndSaveAsync`가 `@Async("translationExecutor")`로 3개 언어를 미리 번역해 캐시(`disaster_alert_translation`)에 적재하고, 캐시 미스는 조회 시점 lazy 번역(`ensureTranslated`/`ensureTranslatedBatch`)이 동기로 보정합니다.

VI/TH 는 2026-08-11 에 제거했습니다 — 법정동 명칭 시드가 EN/JA/ZH 뿐이라 본문만 모국어이고 지역명은 영어로 나오는 반쪽 상태였고, 오역을 검증할 수단도 없었습니다(한글 잔존·기호 개수 같은 기계적 검사는 오역이어도 전부 통과합니다). 이제 번역 대상 언어와 법정동 시드 언어가 **일치**합니다 — 새 언어를 추가할 때 이 둘을 함께 늘리지 않으면 그 불일치가 다시 생깁니다.

모델은 `application.yml`의 `spring.ai.openai.chat` 기본값(`gpt-4o-mini`)이 아니라 `OpenAiTranslationClient.MODEL`이 per-call로 **`gpt-4o`를 오버라이드**합니다. 임베딩·LLM 판정은 그대로 `gpt-4o-mini`를 씁니다.

**`gpt-4o-mini`로 내리지 마세요 — 이미 시도했다가 되돌렸습니다.** 최초 상향 사유는 태국어 음차였고 TH 제거(2026-08-11)로 그 사유는 소멸했지만, mini 복귀를 실측하니 JA에서 발신기관 통용 한자 표기가 7/10 → 5/10으로(곡성군 `谷城郡`→`ゴクソン郡`), `폭염`→`猛暑`가 6/6 → 2/6으로(나머지는 `熱波`) 후퇴했고 지연시간도 줄지 않았습니다. 수치와 조건은 해당 상수의 javadoc에 있습니다.

이 두 지표는 각각 **지명을 `legal_district_translation` 시드에서 주입·치환**하고 **재난 용어 대조표를 프롬프트에 주입**하면 결정론적으로 해결됩니다. 그 둘이 들어가 모델 능력 의존이 사라진 뒤에야 mini 재검토가 의미를 갖습니다 — 요금만 보고 내리지 마세요. (`gpt-4o`도 모르는 지명은 한자를 날조합니다: 수락→`修楽`, 염치→`廉置`.)

**법정동 명칭 번역은 이 파이프라인의 대상이 아닙니다** — `domain/legaldistrict`의 시드 테이블(`legal_district_translation`, EN/JA/ZH) 조회로 처리됩니다. `docs/PRD.md`·`docs/TRD.md`가 두 경로를 하나로 묶어 서술해 온 전례가 있으니 혼동하지 마세요.

이 서브시스템의 as-built 상세 동작은 `specs/005-translation-pipeline/spec.md`·`plan.md` 참고.

### 프론트엔드 구조

`frontend/src/`
- `api/*.ts` — 백엔드 도메인별 파일 하나씩, 얇은 axios 래퍼. `api/axios.ts`에 401 → `/auth/reissue` 리프레시 인터셉터가 포함된 공용 인스턴스가 있습니다 (리프레시 중 들어온 동시 요청은 큐잉했다가 각각 1회만 재시도하며, reissue/login 요청 자체는 재시도하지 않음).
- `lib/queries/`, `lib/mutations/` — `api/*` 위에 구축된 React Query 훅.
- `store/` — Zustand 스토어 (`authStore`, 비로그인 사용자용 `guestFavoriteRegionsStore`, `languageStore`, `signupStore`).
- `app/` — Next.js App Router 페이지: `alerts`(+`[id]`/`map`/`new`), `community`(+`[id]`), `events`(+`[id]`), `stats`(+`charts`), `user`(+`me`/`settings`/`delete`/`[id]`), `login`, `signup`, `notifications`, `missing`, `test`. `missing`은 아직 정적 플레이스홀더이고, `components/MissingPerson.tsx`도 하드코딩 목업이라 어디에서도 사용되지 않습니다 — 실종자 화면을 실제로 구현할 때 이 둘을 먼저 확인하세요.
- `ui/` — 재난 유형/등급 표시용 상수 테이블(`disasterType`, `disasterTypeChip`, `disasterTypeColor`, `level`, `metros`). 컴포넌트가 아니라 매핑 데이터입니다. `metros.ts`가 시도 목록을 하드코딩하고 있어 법정동 절에서 말한 "갱신 누락되기 쉬운 지점" 중 하나입니다.
- 다국어는 `lib/i18n.ts`(i18next + react-i18next)가 `constants/i18n`의 인라인 리소스로 초기화합니다 — 로케일 JSON 파일이 아니라 TS 상수이며, 보간 구분자가 기본 `{{ }}`가 아니라 `{ }`로 설정돼 있습니다.
- `next.config.ts` — `/api/:path*`를 `BASE_API_URL`(미설정 시 운영 API)로 rewrite 하고, `sw.js`/`manifest.json`에 `must-revalidate`를 강제해 PWA 업데이트가 구버전 캐시에 막히지 않게 합니다.
- PWA 지원(`@ducanh2912/next-pwa`)이며 푸시 알림을 위한 Firebase Messaging 서비스워커가 있습니다.
- `scripts/ui-screenshot.mjs`·`auth-screenshot.mjs` — Playwright 기반 화면 점검 스크립트 (ui-checker/ui-fixer agent 가 사용).

## 팀 컨벤션 (`prompts/PROMPTS.md` 기반)

- 백엔드: controller → service → repository 계층 구조. DTO는 응답 형태별로 1개 타입, 리스트는 별도 타입이 아니라 내부에 중첩. 모든 응답은 공용 `ApiResponse`/`ApiErrorResponse` 포맷 사용. 요청 DTO는 `@Valid`로 검증. 도메인 상태 변경은 서비스에서 setter가 아닌 엔티티 메서드를 통해서만 수행. 예외는 `CustomException` + `ErrorCode`로 던지고 임의 방식 금지.
- 메서드는 가독성을 위해 대략 30~50줄 이내로 유지하고, 사소한 필요로 새 라이브러리를 들이지 않기.
- 프론트엔드: React Query 키는 tuple로 구성. 특별한 필요가 없다면 낙관적 업데이트는 지양. DTO 필드명은 백엔드와 정확히 일치시키기.
- **테스트가 있는 로직을 변경하거나 새 순수 로직을 추가할 때는 Red-Green 방식으로 진행**: 먼저 실패하는 테스트를 작성해(Red) 그 테스트를 통과시키는 최소 구현을 작성한다(Green). 두 단계를 한 커밋에 합치지 않는다. 단, 이 저장소의 `./gradlew test`는 CLI로는 한글 경로 문제(JVM `@argfile` classpath 전달이 `sun.jnu.encoding=MS949` 환경에서 깨짐)로 항상 `ClassNotFoundException`이 나서, Claude Code 세션에서 직접 실행해 red/green을 확인할 수는 없다 — `compileJava`(main 소스)가 아니라 `compileTestJava`(또는 `testClasses`)로 새로 쓴 테스트 파일 자체의 import/assertion/타입 오류를 확인한다. 다만 **IntelliJ에서는 실제 실행이 가능**하다 — Run Configuration(또는 JUnit 템플릿)의 "명령줄 단축"을 기본값(`@argfile`)이 아니라 **"JAR 매니페스트 사용"**으로 바꾸면 정상적으로 테스트가 통과한다("없음"은 classpath가 너무 길어서 실패함). 실제 통과 여부는 사용자에게 이 설정으로 IntelliJ에서 실행해달라고 요청해 확인받는다. **커밋은 다른 작업과 마찬가지로 사용자가 명시적으로 요청한 경우에만 한다** — Red-Green 워크플로 자체가 두 단계 커밋을 전제하지만, 그 워크플로 수행이 곧 커밋 승인은 아니다. **예외: `test-writer` agent는 2026-08-12부터 테스트 작성을 마치면 되묻지 않고 `commit-message` 스킬로 바로 커밋한다** (상설 승인, `.claude/agents/test-writer.md` 참고). 이 예외는 커밋까지만이고 `git push`에는 적용되지 않는다.

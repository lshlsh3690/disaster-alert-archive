# Backend — Disaster Alert Archive API

Spring Boot 3.4.4 / Java 17 REST API. 행정안전부 재난문자를 수집해 저장하고, 임베딩 기반으로 "이벤트"로
클러스터링하며, 지역별 위험도를 계산하고, 관심지역 기반 FCM 푸시를 발송한다.

> 저장소 전체 개요는 [루트 README](../README.md), 작업 시 주의점·컨벤션은 [CLAUDE.md](../CLAUDE.md) 참고.

---

## 실행

```bash
# 1) 레포 루트에서 인프라 기동 (Postgres + Redis 만, --profile docker 불필요)
docker compose -f docker-compose.dev.yml up postgres redis

# 2) 레포 루트의 .env.example 을 .env.dev 로 복사해 값을 채운다
cp .env.example .env.dev

# 3) 백엔드 실행
cd backend
./gradlew bootRun
```

- 포트: `8080`, 모든 엔드포인트는 `/api/v1/*`
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Actuator: `/actuator/*`

`docker-compose.dev.yml` 의 `backend`/`frontend` 서비스는 `docker` 프로필에 묶여 있다. 로컬 개발에서는
Postgres/Redis만 컨테이너로 띄우고 애플리케이션은 위처럼 직접 실행하는 것을 권장한다.

### 환경변수

레포 루트 `.env.example` 이 백엔드 환경변수 템플릿이며 `application.yml` / `application-prod.yml` 의
`${...}` 참조와 1:1로 맞춰져 있다. 설정을 추가/제거하면 템플릿도 함께 갱신한다.

임베딩·LLM 판정·번역은 `OPENAI_API_KEY` 하나를 공유한다. FCM 서비스 계정 키만은 환경변수가 아니라
클래스패스 파일 `src/main/resources/firebase-service-account.json` 으로 읽는다 ([FirebaseConfig](src/main/java/com/disaster/alert/alertapi/global/config/FirebaseConfig.java)).

---

## 빌드 / 테스트

```bash
./gradlew compileJava       # main 소스 컴파일만 (빠른 확인 + QueryDSL Q-클래스 재생성)
./gradlew compileTestJava   # 테스트 소스 컴파일만
./gradlew build             # 전체 빌드 + 테스트
./gradlew test              # 전체 테스트 (realApi 태그 제외)
./gradlew test --tests "com.disaster.alert.alertapi.domain.auth.service.AuthServiceTest"
./gradlew realApiTest       # 실제 OpenAI 를 호출하는 테스트만 (@Tag("realApi"))
```

**테스트 종류**

| 종류 | 작성 방식 | 예 |
|------|-----------|-----|
| 순수 로직 단위 테스트 | Spring 컨텍스트 없이 순수 JUnit | `FireAlertClassifierTest`, `DisasterCooldownTest`, `MissingPersonIdentityTest`, `IntensityExtractorTest` |
| 통합 테스트 | `@IntegrationTest` (= `@SpringBootTest` + `test` 프로파일 + `.env.test` 주입) | `AuthServiceTest`, `DisasterAlertServiceTest` |
| 실 API 테스트 | `@Tag("realApi")` — 기본 `test` 태스크에서 제외 | `OpenAiTranslationClientRealApiTest` |

통합 테스트는 슬라이스/목킹이 아니라 실제 테스트용 Postgres에 붙는다 (`backend/.env.test`). dev
docker-compose와 동일한 구성의 DB가 떠 있어야 한다.

`realApi` 테스트는 `OPENAI_API_KEY` 가 필요하고 호출마다 요금이 발생하며 응답이 완전히 결정적이지 않다.
프롬프트 규칙을 바꿨을 때만 수동으로 돌린다.

> ⚠️ Windows + 한글 경로 환경에서는 CLI `./gradlew test` 가 JVM `@argfile` classpath 전달 문제
> (`sun.jnu.encoding=MS949`)로 항상 `ClassNotFoundException` 이 난다. IntelliJ Run Configuration 의
> **"명령줄 단축" 을 "JAR 매니페스트 사용"** 으로 바꾸면 정상 실행된다(기본값 `@argfile` 은 실패,
> "없음" 은 classpath 길이 초과로 실패).

---

## 패키지 구조

`src/main/java/com/disaster/alert/alertapi/`

```
domain/                 # bounded context 별 패키지, 각각 controller → service → repository
  disasteralert/        # 재난문자 수집·조회·통계 (+ tool/DisasterAlertRegionBackfillTool)
  event/                # 이벤트 클러스터링 (model/ 에 순수 판정 로직, service/ 에 클러스터링·LLM)
  risk/                 # 지역 위험도 계산·감쇠·인접 확산
  weather/              # 기상 관측 수집 및 일별 집계
  notification/         # FCM 발송 대상 산출 및 전송
  member/ auth/         # 회원, JWT + OAuth2(Google·Kakao·Naver 자체 구현)
  legaldistrict/        # 법정동 코드 및 명칭 번역
  community/ comment/   # 게시판, 댓글
  useralert/            # 사용자 작성 재난 제보 알림
  openapi/              # 토큰 기반 공개 API (JSON/CSV)
  common/exception      # CustomException / ErrorCode
global/
  security/jwt          # JWT 인증 필터·프로바이더
  exception             # GlobalExceptionHandler
  dto                   # ApiResponse / ApiErrorResponse
  translation           # OpenAI 기반 다국어 번역
  logtrace              # AOP 호출 추적 로깅 (트랜잭션 ID + 들여쓰기)
  service util config redis controller
scheduler/              # DisasterFetchScheduler (재난문자 수집)
api/                    # DisasterOpenApiClient (공공데이터포털)
```

도메인별 스케줄러는 각 도메인 아래에 있다 — `weather/scheduler/WeatherCollectScheduler`,
`WeatherDailySummaryScheduler`, `risk/scheduler/RiskDecayScheduler`. "왜 X가 실행되지 않았는지"
디버깅할 때 가장 먼저 볼 곳이다.

과거 데이터를 현재 로직으로 재처리하는 수동 도구는 `*/tool/*BackfillTool` 에 있다
(`EventClusteringBackfillTool`, `RiskBackfillTool`, `DisasterAlertRegionBackfillTool`).

---

## 규칙

- 계층은 `controller → service → repository`. 응답 DTO는 엔드포인트별 1개 타입으로, 리스트는 별도 타입이 아니라 내부에 중첩한다.
- 모든 응답은 공용 `ApiResponse` / `ApiErrorResponse` 포맷을 쓴다. 요청 DTO는 `@Valid` 로 검증한다.
- 예외는 반드시 `CustomException` + `ErrorCode` 로 던진다. 임의로 에러 바디를 만들지 않는다.
- 도메인 상태 변경은 서비스에서 setter 가 아니라 엔티티 메서드를 통해 수행한다.
- 메서드는 대략 30~50줄 이내로 유지하고, 사소한 필요로 새 라이브러리를 들이지 않는다.

### DB 마이그레이션

Flyway (`src/main/resources/db/migration/V*.sql`, `ddl-auto: validate`). **이미 적용된 마이그레이션은 절대
수정하지 말고** 새 `V{n}__description.sql` 을 추가한다. `out-of-order: true` 라 번호가 어긋난 미적용
마이그레이션도 실행된다. 법정동 번역 시딩과 날씨 이력 백필용 시드 마이그레이션이 다수 존재한다.

### QueryDSL

Q-클래스는 컴파일 시 `src/main/generated` 에 생성된다 (`build.gradle` 에서 경로 지정, `clean` 시 삭제).
리포지토리 쿼리 메서드가 인식되지 않으면 `./gradlew compileJava` 로 재생성한다.

---

## 알아둘 함정

- **FCM 메시지는 반드시 data-only** — `.setNotification()` 을 쓰면 Chrome이 자동 표시와 백그라운드 핸들러
  실행을 동시에 해서 알림이 중복된다. 과거에 고친 버그이므로 이 필드를 되살리지 말 것.
- **클러스터링 임계값은 실데이터로 튜닝된 값** — `application.yml` 의 `clustering.*` 블록에 각 값의 존재
  이유가 한국어 주석으로 달려 있다. 바꾸기 전에 반드시 읽을 것.
- **클러스터링 기능 플래그는 기본 `false`** — `CLUSTERING_ENABLED`, `LLM_FALLBACK_ENABLED`,
  `CROSS_REGION_ENABLED`. 어떤 코드 경로가 실제로 도는지는 대상 환경의 플래그를 확인해야 안다.
- **번역 대상 언어는 EN/JA/ZH 세 개다** — 법정동 명칭 시드와 같은 범위로 맞춘 것이다. VI/TH 는
  2026-08-11 에 제거했다(시드가 없어 지역명이 영어로 나오는 반쪽 상태였고 오역 검증 수단도 없었다).
  새 언어를 추가할 땐 `SupportedLanguage`, `OpenAiTranslationClient.LANGUAGE_NAMES`, 법정동 시드
  마이그레이션, 프론트 `constants/language.ts`·`i18n.ts` 를 함께 늘려야 한다.
- **번역만 상위 모델(`gpt-4o`)을 쓴다** — `spring.ai.openai.chat` 기본값은 `gpt-4o-mini` 지만
  `OpenAiTranslationClient.MODEL` 이 per-call 로 오버라이드한다. **mini 로 내리지 말 것** —
  TH 제거로 최초 상향 사유는 소멸했지만 실제로 되돌려 보니 JA 에서 발신기관 한자 표기 7/10→5/10,
  `폭염`→`猛暑` 6/6→2/6 로 후퇴했다(수치는 해당 상수 javadoc). 지명·용어를 시드/대조표로
  떼어낸 뒤에야 재검토 대상이다.
- **시도 코드 파생(`substring(0,2)`)에 공용 헬퍼가 없다** — `AlertNotificationService`,
  `EventClusteringService`, `DisasterAlertRepositoryImpl` 에 각각 구현돼 있다. 지역 매칭을 건드릴 때 셋을 함께 본다.
- **Sentry 는 logback 연동 모듈이 필수** — 스타터의 `SentryExceptionResolver` 는 MVC에서 완전히 미처리된
  예외만 잡는데, 이 프로젝트는 `GlobalExceptionHandler` 가 모두 처리하고 스케줄러 실패는 애초에 HTTP 요청이
  아니다. `sentry-logback` 이 있어야 `log.error(msg, e)` 가 이벤트로 올라간다.

---

## 서브시스템 명세

as-built 상세 동작(파일:라인 근거 포함)은 레포 루트 `specs/` 에 있다.

| 명세 | 대상 |
|------|------|
| `specs/001-event-clustering-pipeline/` | 이벤트 클러스터링 (37개 기능 요구사항) |
| `specs/002-risk-scoring/` | 위험도 계산·감쇠·확산 |
| `specs/003-fcm-notification/` | FCM 알림 |
| `specs/004-legal-district-matching/` | 법정동 기반 지역 매칭 |
| `specs/005-translation-pipeline/` | 번역 파이프라인 |

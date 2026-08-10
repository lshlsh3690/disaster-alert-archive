# 구현 계획: 번역 파이프라인 (Translation Pipeline)

**브랜치**: `005-translation-pipeline` | **날짜**: 2026-08-10 | **명세**: [spec.md](./spec.md)

**입력**: `/specs/005-translation-pipeline/spec.md`의 기능 명세

**참고**: 이 문서 역시 as-built 문서화다 — 앞으로 구현할 계획이 아니라, `global/translation/*`와 그 소비처가 **실제로 어떻게 구성되어 있는지**를 기록한다. "헌법 검사"는 사후 감사(audit)로 읽어야 한다.

## 요약

번역 파이프라인은 재난문자 본문(`message`)·재난 유형(`disasterType`)·이벤트 제목(`event_title`) 세 필드를 EN/JA/ZH/VI/TH로 번역해 DB에 캐시하는 서브시스템이다. 번역 실행 시점은 **세 갈래**로 나뉜다 — 수집 스케줄러가 새 알림마다 전 언어를 미리 번역하는 비동기 경로, 상세 조회가 캐시 미스를 그 자리에서 메우는 동기 단건 경로, 목록/검색이 페이지 내 미번역분만 메우는 동기 일괄 경로. 엔진은 2026-08-09에 DeepL에서 OpenAI(`gpt-4o-mini`)로 교체되었다.

이 문서의 핵심 산출물은 두 가지다. (1) 세 트리거 경로의 **트랜잭션 경계**를 명시하는 것 — 특히 `getAlertDetail`이 `@Transactional`이라 lazy 번역이 바깥 트랜잭션에 합류하고, 이 때문에 동시 요청 시 원문 폴백이 실패하고 HTTP 500이 나는 구조적 결함이 존재한다. (2) **법정동 명칭 번역이 이 파이프라인의 대상이 아니라는 경계**를 못박는 것 — `docs/PRD.md`·`docs/TRD.md`가 두 경로를 하나로 서술해 온 것이 이 문서화의 직접적 동기다.

## 기술 컨텍스트

**언어/버전**: 백엔드 Java 17 / Spring Boot 3.x

**주요 의존성**: Spring AI 1.0.0(`spring-ai-starter-model-openai`, `ChatClient`), Spring Data JPA, Spring `@Async`(`ThreadPoolTaskExecutor`), Flyway

**저장소**: PostgreSQL — `disaster_alert_translation`(V3), `disaster_event_translation`(V40). 둘 다 `(원본 ID, language_code)` 복합 PK의 캐시 테이블이다.

**테스트**: 두 종류. 길이 가드는 순수 JUnit 단위 테스트(`OpenAiTranslationClientTest`, Spring 컨텍스트·네트워크 불필요), 실제 번역 품질은 실제 OpenAI를 호출하는 통합 테스트(`OpenAiTranslationClientRealApiTest`, `@IntegrationTest`, 고정 원문 1건 × 지원 언어 5개). 후자는 헌법 원칙 III과 충돌한다(아래 헌법 검사 참고).

**대상 플랫폼**: 웹(Spring Boot REST API). 번역 결과는 `?lang=` 쿼리 파라미터로 요청한 클라이언트에 응답 DTO 필드로 전달된다.

**성능 목표**: 코드/설정에 명시된 목표 없음 (spec.md SC-002 참고). 목록 lazy 번역의 순차 호출 횟수 상한이나 타임아웃도 없다.

**제약사항**: OpenAI 자격증명(`spring.ai.openai.api-key`)을 임베딩·LLM 판정과 공유한다. 번역 트래픽이 그 두 기능의 쿼터·요금에 함께 반영된다.

**규모/범위**: 알림 1건당 최대 2회 번역 호출(본문 + 유형) × 5개 언어 = 최대 10회. 수집 스케줄러는 10분 주기(`@Scheduled(cron = "0 0/10 * * * *")`).

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

이 서브시스템은 이미 구현되어 운영 중이므로, 아래는 "게이트 통과 여부"가 아니라 **현재 코드가 각 원칙을 실제로 지키고 있는지에 대한 사후 감사**다.

- **원칙 I (가독성과 단순성 우선) — 대체로 준수, 이원화 1건.**
  `TranslationService`와 `EventTranslationService`는 동일한 3메서드 패턴(`translateAndSaveAsync`는 alert 쪽만 / `ensureTranslated` / `ensureTranslatedBatch` + private `translateAndSaveInternal`)을 의도적으로 복제한 구조다. `EventTranslationService` 클래스 주석이 "alert `TranslationService` 패턴 복제(제목만)"라고 그 의도를 명시하고 있어(`domain/event/service/EventTranslationService.java:20`), "세 줄의 비슷한 코드가 섣부른 공통화보다 낫다"는 원칙 문구에 부합한다. 각 메서드도 30~50줄 이내다.
  다만 **지원 언어 정의가 두 곳으로 갈라져 있다**: `SupportedLanguage` enum(`global/translation/SupportedLanguage.java:18-22`)과 `OpenAiTranslationClient.LANGUAGE_NAMES` 맵(`global/translation/OpenAiTranslationClient.java:51-57`). enum 주석은 "이 enum에만 항목을 추가하면 된다"고 말하지만(`SupportedLanguage.java:14`) 실제로는 맵도 갱신해야 하며, 누락 시 컴파일이 아니라 **런타임 `IllegalArgumentException`**으로 드러난다(`OpenAiTranslationClient.java:70-73`). 004에서 지적한 "중앙화되지 않은 파생 로직이 드리프트를 만든다"와 같은 유형의 위험이다.
  - **권고(문서화 목적, 이번 범위 아님)**: 언어명을 `SupportedLanguage`의 필드로 올리고 `LANGUAGE_NAMES`를 제거한다. `translate()` 시그니처를 `String targetLang` 대신 `SupportedLanguage`로 바꾸면 호출부 2곳(`TranslationService.java:160,165`, `EventTranslationService.java:90`)이 이미 enum을 들고 있으므로 변경 비용이 낮고, 예외 경로 자체가 사라진다.

- **원칙 II (계층형 아키텍처 준수) — 부분 위반 2건, 둘 다 영향은 제한적.**
  1. **예외 규약 위반(확인됨).** `OpenAiTranslationClient`가 `CustomException` + `ErrorCode`가 아니라 raw `IllegalArgumentException`/`IllegalStateException`을 던진다(`OpenAiTranslationClient.java:72,82,87`). 헌법 원칙 II는 "예외는 `CustomException` + `ErrorCode`로만 던진다"고 규정한다.
     **다만 실제 영향은 없다**: 이 예외들은 전부 호출 측에서 잡힌다 — `TranslationService.translateAndSaveInternal`이 broad `catch`로 로그를 남기고 재던지면(`global/translation/TranslationService.java:177-180`), 그 상위 세 진입점이 모두 삼킨다(`TranslationService.java:63-69,89-94,136-140`). 이벤트 쪽도 동일하다(`EventTranslationService.java:49-53,75-79`). 따라서 이 예외가 `GlobalExceptionHandler`에 도달해 임의 형식의 에러 바디를 만드는 경로는 **존재하지 않는다**. `ErrorCode`는 API 응답 포맷을 위한 규약인데 여기는 내부 제어 흐름이므로, 규약의 문언에는 어긋나지만 규약이 방지하려는 문제(비표준 에러 응답)는 발생하지 않는다.
  2. **패키지 배치 비대칭(확인됨).** 재난문자 번역은 `global/translation/TranslationService`에 있으면서 `domain/disasteralert`의 엔티티·리포지토리를 직접 임포트한다(`TranslationService.java:3-6`) — `global` → `domain` 방향 의존이다. 반면 이벤트 제목 번역은 `domain/event/service/EventTranslationService`에 있어 `domain` → `global` 방향으로 정상이다. 같은 성격의 두 서비스가 서로 반대 방향의 의존을 갖는다. `controller → service → repository` 계층 자체를 건너뛰지는 않으므로 헌법 문언의 직접 위반은 아니지만, `global`이 특정 도메인을 아는 구조는 패키지 경계 관점에서 일관적이지 않다.
  - 응답 포맷(`ApiResponse`), DTO 설계는 이 서브시스템이 컨트롤러를 소유하지 않으므로(번역 필드는 소비처 DTO에 실려 나간다) 해당 없음.

- **원칙 III (검증 가능한 변경) — 확인된 위반 2건.**
  1. **외부 AI API 실제 호출.** 헌법은 "외부 AI API 호출(임베딩 등)은 테스트에서 실제 호출 대신 고정된 픽스처를 사용해 결정적으로 검증한다"고 규정하지만, `OpenAiTranslationClientRealApiTest`는 목킹 없이 **실제 `gpt-4o-mini`를 호출한다**(지원 언어 5개). `OPENAI_API_KEY`가 없으면 이 테스트는 스킵이 아니라 실패한다.
  2. **CI 테스트 단계 부재.** 헌법은 "테스트는 항상 빌드 이전의 별도 CI 단계에서 수행한다"고 규정하지만, `.github/workflows/backend-deploy.yml`에는 테스트 잡이 없고 `backend/dockerfile`은 `./gradlew clean bootJar -x test`로 빌드한다. 즉 **배포 파이프라인에서 어떤 테스트도 실행되지 않는다**. 헌법이 "docker build 단계에서 테스트를 실행하지 않는다"고 한 것은 지켜지지만, 그 대안으로 요구한 "빌드 이전 별도 CI 단계"가 존재하지 않는다.
  - 반면 길이 가드는 원칙 III에 부합한다 — `OpenAiTranslationClientTest`가 외부 의존성 없는 순수 JUnit 단위 테스트로 배수(6배)·하한(60자) 경계를 결정적으로 고정한다.
  - **권고**: 실제 호출 테스트를 JUnit 태그로 분리해 기본 실행에서 제외하고, 목킹된 결정적 테스트를 기본 경로에 두는 것. 그 위에 빌드 이전 CI 테스트 잡을 추가해야 원칙 III을 온전히 만족한다.

- **원칙 IV (정직한 문서화) — 이 문서화 과정에서 발견한 문서 결함 1건 + 코드 결함 2건.**
  1. `docs/PRD.md:66`과 `docs/TRD.md:68`이 법정동 명칭을 번역 API의 스케줄러 번역 대상으로 서술한다. 실제로는 `legal_district_translation`이 Flyway 시드 테이블이며 런타임 번역 호출이 없다(`specs/004-legal-district-matching/spec.md` FR-017). 2026-08-09 DeepL→OpenAI 교체 시 문구만 바꾸면서 이 오류가 그대로 옮겨졌다. spec.md FR-012가 이 경계를 `MUST NOT`으로 못박았다.
  2. 동시 요청 시 HTTP 500(아래 "미해결 항목" 참고) — 상세 조회의 원문 폴백이 동시 요청 상황에서 성립하지 않는다.
  3. 지원 언어 범위 불일치 — 본문은 5개 언어, 법정동 시드는 3개 언어(EN/JA/ZH). VI/TH 사용자는 본문은 모국어, 지역명은 영어인 혼합 응답을 받는다.
  셋 다 숨기지 않고 spec.md 예외 상황 절에 남겼다.

- **원칙 V (한글 커밋 컨벤션)** — 이 문서화 작업의 커밋은 `docs(spec):` 형식을 따른다.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/005-translation-pipeline/
├── plan.md                      # 이 파일
├── spec.md                      # 기능 명세 (as-built)
└── checklists/requirements.md   # 명세 품질 체크리스트
```
(`research.md`/`data-model.md`/`quickstart.md`/`contracts/`/`tasks.md`는 이미 구현이 끝난 기존 코드의 사후 문서화이므로 생성하지 않았다 — 001~004와 동일.)

### 소스 코드 (저장소 루트)

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── global/translation/                          # 이 서브시스템의 핵심
│   ├── SupportedLanguage.java                   # 지원 언어 enum(EN/JA/ZH/VI/TH) + lang 파라미터 파싱
│   ├── TranslationProperties.java               # translation.enabled 바인딩
│   ├── OpenAiTranslationClient.java             # 번역 호출 + 프롬프트 + 길이 가드
│   └── TranslationService.java                  # 재난문자 본문/유형 번역 3경로 + DB 캐시
├── domain/event/service/EventTranslationService.java   # 이벤트 제목 번역(같은 패턴 복제)
├── scheduler/DisasterFetchScheduler.java        # 진입점 — 수집 직후 사전 번역 트리거
├── global/config/AsyncConfig.java               # translationExecutor 풀 정의 + @EnableConfigurationProperties
├── domain/disasteralert/service/DisasterAlertService.java   # 소비처(상세 1 + 목록 3)
└── domain/event/service/EventQueryService.java              # 소비처(목록 1 + 상세 1)

backend/src/main/resources/
├── application.yml                              # translation.enabled, spring.ai.openai.chat
└── db/migration/
    ├── V3__create_disaster_alert_translation.sql
    └── V40__create_disaster_event_translation.sql

backend/src/test/java/com/disaster/alert/alertapi/
├── global/translation/OpenAiTranslationClientTest.java              # 순수 단위(길이 가드 경계)
└── global/translation/OpenAiTranslationClientRealApiTest.java                   # 통합(실제 OpenAI 호출, 고정 원문)
```

**구조 결정**: 번역 클라이언트와 재난문자 번역 서비스는 `global/translation`에, 이벤트 제목 번역은 `domain/event`에 배치되어 있다. 소비처(`DisasterAlertService`, `EventQueryService`)는 번역 서비스를 주입받아 조회 로직 안에서 직접 호출하며, 번역 전용 컨트롤러나 엔드포인트는 존재하지 않는다 — 번역은 항상 기존 조회 API의 `?lang=` 파라미터에 얹혀 동작한다.

## 트리거 경로와 트랜잭션 경계

세 경로 모두 `translation.enabled` 게이트를 먼저 통과한다(`TranslationService.java:59,83,114`).

| 경로 | 진입점 | 스레드/트랜잭션 | 실패 격리 단위 |
|------|--------|----------------|----------------|
| ① 수집 시점 사전 번역 | `DisasterFetchScheduler.java:43` → `translateAndSaveAsync` | `@Async("translationExecutor")` + 자체 `@Transactional`(호출자와 분리) | 언어 1개 (`TranslationService.java:63-69`) |
| ② 조회 시점 lazy 단건 | `DisasterAlertService.java:553` / `EventQueryService.java:121` → `ensureTranslated` | 호출자 트랜잭션에 **합류** | 요청 1건 (`TranslationService.java:89-94`) |
| ③ 조회 시점 lazy 일괄 | `DisasterAlertService.java:701,758,817` / `EventQueryService.java:89` → `ensureTranslatedBatch` | 호출자 트랜잭션에 **합류** | 알림 1건 (`TranslationService.java:136-140`) |

**①의 실행 순서**: 수집 스케줄러는 신규 알림 ID마다 번역 → FCM 알림 → 클러스터링 → cross-region을 순서대로 호출한다(`DisasterFetchScheduler.java:41-50`). 번역이 `@Async`라 뒤 세 단계를 블로킹하지 않는다. `translationExecutor`는 core 5 / max 10 / queue 100이다(`global/config/AsyncConfig.java:22-27`).

**②③의 트랜잭션 합류가 만드는 문제**: `getAlertDetail`이 `@Transactional`이므로(`DisasterAlertService.java:541`) `ensureTranslated`의 `@Transactional`은 새 트랜잭션을 열지 않고 바깥에 합류한다. 그 결과 번역 캐시 저장이 **커밋 시점**에 실패하면(복합 PK 충돌 등) `ensureTranslated` 내부의 `catch`를 이미 벗어난 뒤이고, 트랜잭션은 rollback-only로 마킹되어 상세 조회 전체가 `UnexpectedRollbackException`(HTTP 500)으로 끝난다. 즉 **spec.md 시나리오 4-1의 원문 폴백은 단일 요청에서만 성립하고 동시 요청에서는 성립하지 않는다.**

## 데이터 모델

두 캐시 테이블 모두 `(원본 ID, language_code)` 복합 PK다 — 같은 원본·같은 언어에 대해 번역 API를 두 번 호출하지 않기 위한 멱등 키다.

**`disaster_alert_translation`** (`V3__create_disaster_alert_translation.sql:1-12`)

| 컬럼 | 비고 |
|------|------|
| `disaster_alert_id`, `language_code` | 복합 PK. `disaster_alert`에 FK |
| `translated_message` | `NOT NULL` — 본문 번역이 실패하면 행 자체를 만들지 않는다 |
| `translated_disaster_type` | nullable — 유형 번역만 실패해도 본문은 저장한다(`TranslationService.java:162-168`) |
| `translated_region_names` | **항상 `null`로 저장된다** |
| `translated_at` | |

`translated_region_names`가 항상 `null`인 이유: 지역명은 이 파이프라인이 아니라 `legal_district_translation` 시드 테이블에서 조회하기 때문이다. 같은 법정동이 여러 재난문자에 반복 등장하므로 알림별로 번역해 저장하는 것이 낭비라는 판단이며, 이 의도는 `TranslationService` 클래스 주석(`TranslationService.java:29-31`)과 저장 지점 주석(`TranslationService.java:171`)에 남아 있다. 컬럼 자체는 V3 스키마에 남아 있어 **사실상 사용되지 않는 컬럼**이다.

**`disaster_event_translation`** (`V40__create_disaster_event_translation.sql:3-9`)

`(disaster_event_id, language_code)` 복합 PK, `translated_title NOT NULL`, `disaster_events`에 `ON DELETE CASCADE`. V40 헤더 주석이 "disaster_alert_translation 패턴 복제"임을 명시한다.

## 엔진 교체 이력 (2026-08-09)

DeepL Free는 계정당 **월간이 아니라 평생 누적** 쿼터라 소진 후 리셋되지 않는다. 운영에서 주 키와 예비 키가 모두 456 Quota exceeded로 막혀 번역이 영구 정지됐고(로그상 요청마다 두 키를 순회하며 실패 왕복만 반복), 외국어 조회 시 재난문자 본문이 한국어 원문 그대로 노출됐다. 새 Free 키를 계속 발급받는 방식이 지속 가능하지 않아 이미 임베딩·LLM 판정에 쓰던 OpenAI로 옮겼다.

교체와 **함께** 바뀐 것들:

| 항목 | 변경 |
|------|------|
| 클라이언트 | `DeepLTranslationClient`(2키 폴백, HTTP 456 감지) 삭제 → `OpenAiTranslationClient`(Spring AI `ChatClient`) 신설 |
| 설정 | `deepl.api-key`/`api-key2`/`api-url`/`enabled` 제거 → `translation.enabled` 단일 키. 자격증명은 `spring.ai.openai.api-key` 공유이므로 **번역 전용 환경변수가 없다** |
| 프롬프트 | DeepL에는 지시할 방법이 없던 재난문자 표기 규칙(대괄호 발신 기관, `▲ · ※` 기호, "발효"의 경보 의미, 붙여 쓴 행정 용어)을 프롬프트로 규정 |
| 길이 가드 | 신설 — LLM이 설명·머리말을 덧붙일 수 있어 원문 대비 6배(하한 60자) 초과 시 폐기. DeepL 시절에는 불필요했다 |
| 상세 조회 예외 처리 | `ensureTranslated`에 `try/catch` 추가 — 쿼터 소진 기간에 외국어 상세 조회가 전부 500으로 떨어지고 Sentry가 도배된 사고의 후속 조치. 목록(`ensureTranslatedBatch`)은 원래부터 삼키고 있었다 |

번역 캐시는 소급 재번역되지 않으므로, 교체 이전 DeepL 번역과 이후 OpenAI 번역이 목록에 섞여 어투가 다를 수 있다.

## 미해결 항목

| # | 항목 | 고쳐야 할 위치 |
|---|------|----------------|
| 1 | **동시 요청 PK 충돌 → HTTP 500.** "존재 확인 → 저장"이 원자적이지 않아 같은 `(alertId, languageCode)`를 동시 저장하면 커밋 시점에 충돌하고, 호출자 트랜잭션이 rollback-only가 되어 원문 폴백 대신 500이 난다. PostgreSQL `ON CONFLICT DO NOTHING` UPSERT를 리포지토리에 추가하거나 저장을 `REQUIRES_NEW`로 격리해야 한다. | `global/translation/TranslationService.java:81-95,112-144,170-173`, `domain/disasteralert/repository/DisasterAlertTranslationRepository.java`(UPSERT 메서드 신설), `domain/event/service/EventTranslationService.java:41-53,58-80,91`, `domain/event/repository/DisasterEventTranslationRepository.java` |
| 2 | **`SupportedLanguage` ↔ `LANGUAGE_NAMES` 이원화.** enum에만 언어를 추가하면 런타임 `IllegalArgumentException`이 난다. 언어명을 enum 필드로 올리고 `translate()` 시그니처를 `SupportedLanguage`로 바꾸면 예외 경로가 사라진다. | `global/translation/SupportedLanguage.java:14,18-22`, `global/translation/OpenAiTranslationClient.java:51-57,69-73`, 호출부 `TranslationService.java:160,165`·`EventTranslationService.java:90` |
| 3 | **번역 대상 언어(5개) vs 법정동 시드 언어(3개) 불일치.** VI/TH 사용자는 본문은 모국어, 지역명은 영어로 본다. VI/TH 법정동 시드 마이그레이션을 추가하거나, 지원 언어를 시드 범위로 좁히거나, 혼합 표시를 의도된 동작으로 문서화하는 세 선택지가 있다. | `backend/src/main/resources/db/migration/`(VI/TH 시드 신설 시), `global/translation/SupportedLanguage.java:18-22`(범위 축소 시), `domain/legaldistrict/service/LegalDistrictTranslationService.java`(폴백 정책) |
| 4 | **실제 OpenAI 호출 테스트가 기본 실행에 포함 + CI 테스트 단계 부재.** 헌법 원칙 III 위반 2건(위 헌법 검사 참고). | `backend/src/test/java/.../OpenAiTranslationClientRealApiTest.java`(태그 분리), `backend/build.gradle`(태그 제외 설정), `.github/workflows/backend-deploy.yml`(빌드 이전 테스트 잡 신설) |
| 5 | **`translated_region_names` 컬럼이 항상 `null`.** 사실상 미사용 컬럼이다. 제거하려면 Flyway 마이그레이션이 필요하고, 남겨두려면 "의도적으로 비워둠"을 스키마 주석에 남기는 편이 낫다. | `backend/src/main/resources/db/migration/`(새 마이그레이션), `domain/disasteralert/model/DisasterAlertTranslation.java` |

## 이 파이프라인이 건드리지 않는 경계

- **임베딩·클러스터링·위험도**: `EventClusteringService`는 한국어 원문(`disaster_alert.message`)만 임베딩한다(`domain/event/service/EventClusteringService.java:172,205`). 번역 엔진 교체·번역 실패가 벡터·코사인 유사도·이벤트 구성·위험도 점수를 바꾸지 않는다(spec.md FR-017). 2026-08-09 교체 당시 두 DeepL 키가 모두 소진돼 번역이 전무한 상태에서도 이벤트·위험도가 정상 동작한 것이 이 독립성의 실증이다.
- **법정동 명칭 번역**: `legal_district_translation`은 Flyway 시드 테이블이고 런타임 번역 호출이 없다(spec.md FR-012). 상세는 `specs/004-legal-district-matching/spec.md` FR-017·FR-018에 위임한다.
- **프론트엔드 언어 선택 상태**: `languageStore`와 `?lang=` 파라미터 전달 경로는 범위 밖이다 — 백엔드가 `lang`을 수신한 시점부터를 다룬다.
- **사용자 제보 알림(`user_disaster_alert`)**: 번역 경로를 타지 않는다. `translateAndSaveInternal`은 `disasterAlertRepository`(공식 재난문자)만 조회한다(`TranslationService.java:153`). 이 전제가 프롬프트 인젝션 위험 평가의 근거이므로, 사용자 생성 콘텐츠를 번역 대상에 추가하면 재평가가 필요하다.

## 복잡도 추적

> **헌법 검사에서 위반 사항이 있어 정당화가 필요한 경우에만 작성**

| 위반 사항 | 필요한 이유 | 더 단순한 대안을 거부한 이유 |
|-----------|------------|-------------------------------------|
| `OpenAiTranslationClient`가 raw `IllegalArgumentException`/`IllegalStateException`을 던짐 (원칙 II) | 이 예외들은 API 응답이 아니라 "이 번역은 실패했으니 원문으로 폴백하라"는 내부 신호다. `ErrorCode`는 클라이언트에 나갈 에러 코드를 정의하는 열거형인데, 사용자에게 전달되지 않는 내부 신호마다 항목을 추가하면 열거형이 실제 API 계약과 무관한 값으로 오염된다 | 호출 측이 전부 삼켜 `GlobalExceptionHandler`에 도달하지 않음을 확인했다(위 원칙 II 감사). 규약이 방지하려는 문제(비표준 에러 바디 노출)가 발생하지 않으므로 이번 문서화 범위에서 변경하지 않았다. 다만 "문언상 위반"이라는 사실 자체는 숨기지 않고 기록한다 |
| 재난문자 번역은 `global/`, 이벤트 제목 번역은 `domain/event/`에 배치 (원칙 II) | 재난문자 번역이 먼저 만들어졌고 이벤트 제목 번역은 나중에 그 패턴을 복제하면서 자기 도메인 안에 놓인 것으로 보인다(코드·커밋에 명시적 근거 없음 — 추정) | 이미 배포되어 동작 중이고, 패키지 이동은 임포트 경로 변경 외에 기능적 이득이 없다. 다만 `global`이 특정 도메인(`disasteralert`)을 아는 구조라는 사실은 기록한다 |
| 실제 OpenAI를 호출하는 통합 테스트 + CI 테스트 단계 부재 (원칙 III) | 프롬프트가 요구하는 표기 규칙(FR-014)의 실제 준수 여부는 목킹으로 검증할 수 없다 — 모델 응답 자체가 검증 대상이기 때문이다 | 목킹 테스트로 대체 불가능한 검증이라 실제 호출 테스트 자체는 유지할 가치가 있으나, **기본 실행 경로에서 분리하지 않은 것**은 정당화되지 않는다(미해결 항목 #4). CI 테스트 단계 부재도 마찬가지로 정당화 사유가 없는 공백이다 |

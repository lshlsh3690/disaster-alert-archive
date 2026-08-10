# 기능 명세: 번역 파이프라인 (Translation Pipeline)

**기능 브랜치**: `005-translation-pipeline`

**생성일**: 2026-08-10

**상태**: 구현 완료 (Retroactive — 이미 구현된 시스템의 as-built 문서화)

**입력**: `backend/src/main/java/com/disaster/alert/alertapi/global/translation/` 패키지 전체, 이를 소비하는 `domain/disasteralert`·`domain/event`, 그리고 `scheduler/DisasterFetchScheduler` 소스 코드 분석 결과

> 본 문서는 speckit 표준 워크플로(스펙 → 계획 → 구현)를 거치지 않고 이미 운영 중인
> 코드를 역으로 분석해 작성한 **as-built 명세**다. "사용자 스토리"는 향후 요구사항이
> 아니라 현재 코드가 실제로 수행하는 동작을 우선순위(중심성) 순으로 기술한 것이며,
> 모든 FR/시나리오는 실제 소스 코드 라인을 인용한다.
>
> **작성 동기**: 번역이 "언제" 실행되는지(사전 번역 / 조회 시점 lazy)가 코드 여러 곳에
> 흩어져 있고, 특히 **법정동 명칭 번역은 이 파이프라인의 대상이 아닌데도**
> `docs/PRD.md`·`docs/TRD.md`가 두 경로를 하나로 묶어 서술해 왔다. 이 명세는 그 경계를
> 못박는 것을 주요 목적으로 한다.

## 사용자 시나리오 및 테스트 *(필수)*

### 사용자 스토리 1 - 수집 시점 전 언어 사전 번역 (우선순위: P1)

재난문자 수집 스케줄러가 새 재난문자를 저장하면, 그 즉시 지원 언어 전체(EN/JA/ZH/VI/TH)로
비동기 번역을 시작해 캐시에 적재한다. 사용자가 외국어로 접속하기 전에 번역이 준비되어
있어야 조회가 빠르기 때문이다.

**우선순위 이유**: 번역 파이프라인의 정상 진입점이며, 이 경로가 동작하면 이후의 조회 시점
lazy 번역(P2)은 캐시 미스 보정용으로만 쓰인다. 이 경로가 막히면 모든 부담이 조회 시점으로
전가되어 사용자 응답 시간에 직접 노출된다.

**독립 테스트 방법**: 새 재난문자가 저장된 뒤 `disaster_alert_translation`에 해당
`disaster_alert_id`에 대해 5개 언어 행이 생성되는지 확인.

**인수 시나리오**:

1. **Given** 수집 스케줄러가 10분 주기로 기동됨(`@Scheduled(cron = "0 0/10 * * * *")`), **When** `alertService.saveData(raw)`가 신규 알림 ID 목록을 반환함, **Then** 각 ID에 대해 `translationService.translateAndSaveAsync(alertId)`가 호출된다 — `backend/src/main/java/com/disaster/alert/alertapi/scheduler/DisasterFetchScheduler.java:29,39,43`
2. **Given** `translateAndSaveAsync` 호출, **When** `@Async("translationExecutor")`로 별도 스레드 풀에 위임됨, **Then** `SupportedLanguage.values()` 전체(EN/JA/ZH/VI/TH)에 대해 순차 번역이 시도된다 — `backend/src/main/java/com/disaster/alert/alertapi/global/translation/TranslationService.java:56-58,62`
3. **Given** 5개 언어 중 하나가 번역 API 오류로 실패, **When** 해당 언어의 예외가 발생함, **Then** 그 언어만 `warn` 로그로 스킵하고 나머지 언어는 계속 진행한다 — `TranslationService.java:63-69`
4. **Given** `translation.enabled=false`, **When** `translateAndSaveAsync`가 호출됨, **Then** 아무 번역도 시도하지 않고 즉시 반환한다 — `TranslationService.java:59-61`

---

### 사용자 스토리 2 - 조회 시점 lazy 번역 (캐시 미스 보정) (우선순위: P2)

사용자가 외국어로 재난문자 목록이나 상세를 조회할 때 해당 언어 번역이 캐시에 없으면,
그 자리에서 동기로 번역해 저장한 뒤 응답한다. 사전 번역(P1)이 도입되기 전에 쌓인 과거
알림, 또는 사전 번역이 실패한 건을 보정하는 경로다.

**우선순위 이유**: 사전 번역이 커버하지 못하는 구간(과거 데이터, 실패 건)을 메우는 안전망.
다만 동기 호출이라 사용자 응답 시간에 직접 반영된다.

**독립 테스트 방법**: 번역 캐시가 비어 있는 알림을 `?lang=ja`로 조회했을 때 응답에
`translatedMessage`가 채워지고, 두 번째 요청은 번역 API 호출 없이 즉시 반환되는지 확인.

**인수 시나리오**:

1. **Given** `lang` 파라미터가 지원 언어이고 해당 알림의 번역 캐시가 없음, **When** `getAlertDetail`이 호출됨, **Then** `ensureTranslated(id, language)`가 그 자리에서 번역·저장한다 — `backend/src/main/java/com/disaster/alert/alertapi/domain/disasteralert/service/DisasterAlertService.java:551-553`; `TranslationService.java:81-95`
2. **Given** 목록/검색 응답에 담길 알림 ID 목록, **When** `ensureTranslatedBatch(alertIds, language)`가 호출됨, **Then** 이미 번역된 ID를 1쿼리로 조회한 뒤 **누락분만** 순차 번역한다 — `TranslationService.java:112-144`; 호출부 `DisasterAlertService.java:701,758,817`
3. **Given** `lang`이 `"ko"`이거나 미지정, **When** `SupportedLanguage.fromRequestParam(lang)`이 `Optional.empty()`를 반환함, **Then** 번역 경로 자체가 실행되지 않고 번역 필드는 모두 `null`로 응답한다 — `backend/src/main/java/com/disaster/alert/alertapi/global/translation/SupportedLanguage.java:44-51`
4. **Given** 일괄 번역 중 특정 알림 1건이 실패, **When** 예외가 발생함, **Then** 그 건만 스킵하고 나머지 알림 번역을 계속한다 — `TranslationService.java:136-140`

---

### 사용자 스토리 3 - 이벤트 제목 번역 (우선순위: P3)

클러스터링으로 생성된 이벤트의 제목(`event_title`)도 같은 lazy 패턴으로 번역해 이벤트
목록·상세를 외국어로 제공한다.

**우선순위 이유**: 재난문자 본문 번역(P1/P2)과 동일한 패턴을 복제한 부수 경로. 이벤트
타임라인에 표시되는 개별 알림 번역은 재난문자 쪽 캐시를 재사용하므로 여기서는 제목만 다룬다.

**독립 테스트 방법**: 이벤트 목록을 `?lang=zh`로 조회했을 때 `disaster_event_translation`에
해당 `(disaster_event_id, 'ZH')` 행이 생성되는지 확인.

**인수 시나리오**:

1. **Given** 이벤트 목록 조회에 `lang`이 지정됨, **When** `EventQueryService.list`가 실행됨, **Then** `eventTranslationService.ensureTranslatedBatch(eventIds, language)`가 미번역 제목만 번역한다 — `backend/src/main/java/com/disaster/alert/alertapi/domain/event/service/EventQueryService.java:89`
2. **Given** 이벤트 상세 조회에 `lang`이 지정됨, **When** `EventQueryService.detail`이 실행됨, **Then** `eventTranslationService.ensureTranslated(id, language)`가 호출된다 — `EventQueryService.java:121`
3. **Given** 이벤트 제목 번역 실패, **When** 예외가 발생함, **Then** 예외를 삼키고 원문 제목으로 폴백한다 — `backend/src/main/java/com/disaster/alert/alertapi/domain/event/service/EventTranslationService.java:49-53`

---

### 사용자 스토리 4 - 번역 실패 시 원문 폴백 (우선순위: P2)

번역 API 장애·쿼터 소진·비정상 응답 등 어떤 이유로든 번역을 얻지 못하면, 오류 화면 대신
한국어 원문을 그대로 보여준다. 재난 정보는 번역이 없더라도 전달되는 것이 우선이다.

**우선순위 이유**: 2026-08-09 DeepL 두 키가 모두 쿼터 소진되었을 때 목록은 원문 폴백으로
동작했지만 상세 조회는 500으로 떨어져 Sentry가 도배된 실제 사고가 있었다. 이 스토리는
그 사고의 교훈이 반영된 경로다.

**독립 테스트 방법**: 번역 클라이언트가 예외를 던지도록 한 뒤 외국어 목록·상세 조회가
모두 HTTP 200으로 원문을 반환하는지 확인.

**인수 시나리오**:

1. **Given** 번역 클라이언트가 예외를 던짐, **When** 상세 조회가 `ensureTranslated`를 호출함, **Then** 예외를 삼키고 `warn` 로그만 남긴 뒤 번역 필드 `null`(원문 노출)로 200 응답한다 — `TranslationService.java:89-94`
2. **Given** 번역문이 원문 길이의 6배를 초과함(설명 혼입 의심), **When** 길이 가드가 판정함, **Then** 해당 번역을 저장하지 않고 예외를 던져 원문 폴백시킨다 — `backend/src/main/java/com/disaster/alert/alertapi/global/translation/OpenAiTranslationClient.java:43,49,85-90,112-117`
3. **Given** 본문 번역은 성공했으나 재난 유형 번역만 실패, **When** 유형 번역 예외가 발생함, **Then** 유형만 `null`로 두고 본문 번역은 정상 저장한다(부분 번역 허용) — `TranslationService.java:162-168`

---

### 예외 상황

- **동시 요청 시 상세 조회가 500이 된다 (알려진 결함).** `ensureTranslated`는 "존재 확인 → 저장"이 원자적이지 않다. 같은 `(alertId, languageCode)`를 동시 요청 두 개가 모두 미존재로 판단한 뒤 저장하면 복합 PK 충돌이 **flush/commit 시점**에 발생하는데, 이는 `ensureTranslated`의 `catch` 블록을 이미 벗어난 뒤다. 게다가 호출부 `getAlertDetail`이 `@Transactional`이라(`DisasterAlertService.java:541`) 트랜잭션이 rollback-only로 마킹되어, 커밋 시 `UnexpectedRollbackException` → HTTP 500이 된다. 즉 시나리오 4-1의 원문 폴백이 **동시 요청 상황에서는 성립하지 않는다**. 목록 경로(`ensureTranslatedBatch`)도 같은 구조다. UPSERT(`ON CONFLICT`) 또는 저장 트랜잭션 격리가 필요하다 (`TranslationService.java:81-95,112-144`; `V3__create_disaster_alert_translation.sql:8-9`).
- **enum에만 언어를 추가하면 런타임 예외가 난다 (알려진 결함).** `SupportedLanguage`의 주석은 "새 언어 추가 시 이 enum에만 항목을 추가하면 된다"고 기술하지만(`SupportedLanguage.java:14`), 실제로는 `OpenAiTranslationClient.LANGUAGE_NAMES`(`OpenAiTranslationClient.java:51-57`)도 함께 갱신해야 한다. 누락하면 `translate()`가 `IllegalArgumentException`을 던진다(`OpenAiTranslationClient.java:70-73`). 두 곳이 이원화되어 있고 컴파일 타임에 강제되지 않는다.
- **VI/TH 사용자는 지역명만 영어로 보게 된다.** 재난문자 본문은 EN/JA/ZH/VI/TH 5개 언어로 번역되지만(`SupportedLanguage.java:18-22`), 법정동 명칭 시드는 EN/JA/ZH 3개뿐이다(`V7__seed_legal_district_translation_en.sql`, `V15__…_ja.sql`, `V16__…_zh.sql`, `V113__…`). 법정동 번역 조회는 요청 언어에 시드가 없으면 영어로 폴백하므로(`domain/legaldistrict/service/LegalDistrictTranslationService.java`), 베트남어·태국어 사용자는 본문은 모국어, 지역명은 영어인 혼합 응답을 받는다. 이는 두 파이프라인이 **별개**이기 때문에 생기는 구조적 결과다(FR-012 참고).
- 번역 API가 빈 문자열을 반환하면 저장하지 않고 예외를 던진다 — `OpenAiTranslationClient.java:80-83`.
- `translation.enabled=false`이면 사전 번역·lazy 번역 3개 경로 모두 즉시 반환하며, 응답은 항상 원문이 된다 — `TranslationService.java:59,83,114`; `EventTranslationService.java:43,60`.
- 목록 lazy 번역은 미번역 건수만큼 번역 API를 **순차 호출**한다. 알림 1건당 최대 2회(본문 + 유형)이므로 20건 페이지의 첫 외국어 조회는 최대 40회 왕복이 응답 시간에 그대로 더해진다 — `TranslationService.java:135-141,160,165`. 타임아웃·상한 건수 제한은 코드에 없다.

## 요구사항 *(필수)*

### 기능 요구사항

- **FR-001**: 시스템은 재난문자 수집 직후 신규 알림 각각에 대해 지원 언어 전체를 비동기로 사전 번역해야 한다(MUST) (`DisasterFetchScheduler.java:39-43`; `TranslationService.java:56-70`).
- **FR-002**: 사전 번역은 전용 스레드 풀 `translationExecutor`(core 5 / max 10 / queue 100)에서 실행되어 수집·알림 발송·클러스터링 경로를 블로킹하지 않아야 한다(MUST) (`global/config/AsyncConfig.java:22-27`; `DisasterFetchScheduler.java:43,45,47`).
- **FR-003**: 지원 번역 언어는 `SupportedLanguage` enum이 단일 정의해야 한다(MUST) — 현재 EN/JA/ZH/VI/TH 5개. 한국어(`ko`)는 원본이므로 enum에 포함하지 않는다 (`SupportedLanguage.java:18-22`).
- **FR-004**: 요청 파라미터 `lang`이 `null`·공백·`"ko"`이거나 미지원 코드이면 번역 경로를 실행하지 않고 번역 필드를 `null`로 응답해야 한다(MUST) (`SupportedLanguage.java:44-51`).
- **FR-005**: 재난문자 상세 조회는 해당 언어 번역이 캐시에 없을 때 조회 시점에 동기 번역·저장해야 한다(MUST) (`DisasterAlertService.java:551-553`; `TranslationService.java:81-95`).
- **FR-006**: 목록·검색 조회는 페이지에 포함된 알림 중 **미번역 건만** 선별해 번역해야 하며(MUST), 이미 번역된 ID 조회는 1회 쿼리로 수행해야 한다(MUST) (`TranslationService.java:119-133`; 호출부 `DisasterAlertService.java:701,758,817`).
- **FR-007**: 이벤트 제목 번역은 재난문자와 동일한 lazy 패턴(단건 `ensureTranslated` / 일괄 `ensureTranslatedBatch`)으로 동작해야 한다(MUST) (`EventTranslationService.java:41-80`; `EventQueryService.java:89,121`).
- **FR-008**: 번역 결과는 `disaster_alert_translation(disaster_alert_id, language_code)`·`disaster_event_translation(disaster_event_id, language_code)` 복합 PK 테이블에 캐시해야 하며(MUST), 동일 키에 대해 번역 API를 두 번 호출하지 않아야 한다(MUST) (`V3__create_disaster_alert_translation.sql:1-12`; `V40__create_disaster_event_translation.sql:3-9`).
- **FR-009**: 번역 실패는 상위로 전파하지 않고 삼켜 원문(한국어)으로 폴백해야 한다(MUST). 사전 번역은 언어 단위로, 일괄 번역은 알림 단위로, 단건 번역은 요청 단위로 격리해야 한다(MUST) (`TranslationService.java:63-69,89-94,136-140`; `EventTranslationService.java:49-53,75-79`).
- **FR-010**: 재난 유형(`disasterType`) 번역 실패는 본문 번역 결과를 무효화하지 않아야 한다(MUST) — 유형만 `null`로 저장하고 본문은 정상 저장한다 (`TranslationService.java:162-168`).
- **FR-011**: 시스템은 `translation.enabled` 설정으로 번역 기능 전체를 비활성화할 수 있어야 한다(MUST). 비활성화 시 모든 진입점이 즉시 반환해야 한다(MUST) (`application.yml`의 `translation.enabled: ${TRANSLATION_ENABLED:true}`; `global/translation/TranslationProperties.java`; `TranslationService.java:59,83,114`).
- **FR-012**: **법정동 명칭 번역은 이 파이프라인의 대상이 아니다**(MUST NOT). 지역명은 Flyway로 시드된 `legal_district_translation` 테이블을 조회할 뿐 런타임 번역 API 호출이 없다. 이 명세의 대상은 재난문자 `message`/`disasterType`과 이벤트 `event_title` 세 필드로 한정된다 (`TranslationService.java:23-31` 클래스 주석; `V6__create_legal_district_translation.sql`; 상세는 `specs/004-legal-district-matching/spec.md` FR-017·FR-018).
- **FR-013**: 번역 엔진은 OpenAI 계열 모델을 사용하며(MUST), 같은 원문에 같은 번역이 나오도록 결정적으로(temperature 0) 호출해야 한다(MUST) (`OpenAiTranslationClient.java:30,33,74-79`).
- **FR-014**: 번역 프롬프트는 재난문자 표기 규칙을 명시해야 한다(MUST): 대괄호 발신 기관 표기의 대괄호 유지, `▲ · ※` 기호·줄 구조 보존, "발효/해제/특보/주의보/경보"를 경보 용어로 해석, 붙여 쓴 행정 용어를 단일 용어로 인식, 원문에 없는 내용 추가 금지, 번역문만 출력 (`OpenAiTranslationClient.java:120-136`, 규칙 목록은 `124-131`).
- **FR-015**: 시스템은 번역문이 원문 대비 비정상적으로 길면(6배 초과, 단 60자까지는 무조건 허용) 설명 혼입으로 간주해 폐기하고 원문 폴백시켜야 한다(MUST) — 재난문자는 안전 정보라 원문에 없는 내용이 섞이는 것이 번역 실패보다 위험하다 (`OpenAiTranslationClient.java:43,49,112-117`).
- **FR-016**: 번역 API가 빈 응답을 반환하면 저장하지 않아야 한다(MUST) (`OpenAiTranslationClient.java:80-83`).
- **FR-017**: **번역은 임베딩·클러스터링·위험도에 영향을 주지 않아야 한다**(MUST NOT). 이벤트 클러스터링은 한국어 원문(`disaster_alert.message`)만 임베딩하므로, 번역 엔진 교체나 번역 실패가 벡터·코사인 유사도·이벤트 구성·위험도 점수를 바꾸지 않는다 (`domain/event/service/EventClusteringService.java:172,205`).

### 주요 엔티티

- **DisasterAlertTranslation**: 재난문자 번역 캐시. PK=(`disasterAlertId`,`languageCode`). 필드: `translatedMessage`(NOT NULL), `translatedDisasterType`(nullable — 유형 번역만 실패 가능), `translatedRegionNames`(항상 `null`로 저장 — 지역명은 FR-012에 따라 별도 경로), `translatedAt`.
- **DisasterEventTranslation**: 이벤트 제목 번역 캐시. PK=(`disasterEventId`,`languageCode`). 필드: `translatedTitle`(NOT NULL), `translatedAt`. `disaster_events`에 `ON DELETE CASCADE`.
- **SupportedLanguage**: 지원 언어 enum. 요청 파라미터는 소문자(`en`), DB `language_code` 컬럼은 대문자(`EN`)로 관리. 현재 EN/JA/ZH/VI/TH.
- **(참조, 이 서브시스템이 소유하지 않음)**: `LegalDistrictTranslation` — 시드 테이블이며 이 파이프라인의 대상이 아니다(FR-012). 시드 언어는 EN/JA/ZH 3개로 이 파이프라인의 5개 언어와 범위가 다르다.

## 성공 기준 *(필수)*

### 측정 가능한 결과

- **SC-001**: 코드에 명시된 유일한 정량 임계값은 번역문 길이 가드(원문 대비 6배, 하한 60자)이며, 이 값은 `OpenAiTranslationClientTest`의 경계 테스트로 고정되어 있다 (`OpenAiTranslationClient.java:43,49`; `backend/src/test/java/com/disaster/alert/alertapi/global/translation/OpenAiTranslationClientTest.java`).
- **SC-002**: 번역 응답 시간·처리량·비용 상한에 대한 SLA는 코드·설정 어디에도 정의되어 있지 않다 — 측정되지 않음. 목록 lazy 번역의 순차 호출 횟수(미번역 건수 × 최대 2)에 대한 상한도 없다.
- **SC-003**: 자동화 검증은 두 종류뿐이다. 길이 가드는 순수 단위 테스트(`OpenAiTranslationClientTest`)로, 실제 번역 품질은 실제 API를 호출하는 통합 테스트(`OpenAiTranslationClientRealApiTest`)로 검증한다. 후자는 `OPENAI_API_KEY`가 없으면 실패하며, `backend/dockerfile`이 `bootJar -x test`로 빌드하고 별도 CI 테스트 잡이 없어 **배포 파이프라인에서는 어떤 테스트도 실행되지 않는다**.
- **SC-004**: 프롬프트 표기 규칙(FR-014) 중 자동 검증되는 항목은 세 가지다 — 지원 언어 5개 전체에서 ①번역문에 한글이 남지 않을 것 ②대괄호 발신 기관 표기가 보존될 것 ③`▲` 불릿 개수가 원문과 같을 것(`OpenAiTranslationClientRealApiTest`). "발효"의 경보 의미 해석, 붙여 쓴 행정 용어 인식, 원문에 없는 내용 미추가는 **자동 검증되지 않는다** — 길이 가드(FR-015)가 극단적인 설명 혼입만 걸러낼 뿐이다.
- **SC-005**: 이 통합 테스트는 고정 원문 1건을 사용해 실행마다 같은 입력을 보낸다. 다만 LLM 응답 자체가 완전히 결정적이지는 않아(temperature 0도 보장은 아님) 간헐적 실패 가능성이 남는다 — 실제로 실 DB의 최신 재난문자를 쓰던 이전 버전에서는 목록에 `[태안군]`이 들어온 회차에만 TH가 실패했다.

## 가정

- 번역 대상 원문은 정부(행정안전부)가 발신한 재난문자와 그로부터 파생된 이벤트 제목뿐이며, 사용자가 작성한 텍스트는 번역 경로를 타지 않는다고 가정한다 — `translateAndSaveInternal`은 `disasterAlertRepository`(공식 재난문자)만 조회하고, `user_disaster_alert`(사용자 제보)는 번역 대상에 포함되지 않는다 (`TranslationService.java:153-155`). 이 가정 때문에 프롬프트 인젝션 위험을 낮게 평가하고 있으며, 사용자 생성 콘텐츠가 번역 대상에 추가되면 이 전제는 무효가 된다.
- 번역 엔진은 2026-08-09에 DeepL에서 OpenAI로 교체되었다. DeepL Free는 계정당 **평생 누적** 쿼터라 소진 후 리셋되지 않으며, 주 키·예비 키가 모두 456 Quota exceeded로 막혀 번역이 영구 정지된 것이 교체 사유다. 교체 이전에 DeepL로 번역되어 캐시에 남은 행은 소급 재번역되지 않으므로, 목록에 두 엔진의 번역이 섞여 어투가 다를 수 있다.
- 사전 번역(P1)이 정상 동작하는 한 조회 시점 lazy 번역(P2)은 과거 데이터에만 적용된다고 가정한다. 다만 사전 번역이 언제 도입되었는지, 그 이전 알림 중 미번역분이 몇 건인지는 운영 DB 조회 없이 확인하지 못했다 — 첫 외국어 조회의 실제 지연(SC-002)은 이 미번역 잔량에 비례한다.
- OpenAI 자격증명은 `spring.ai.openai.api-key` 하나를 임베딩·LLM 판정(클러스터링 cross-region, 위험도 프로파일링)과 공유한다. 번역 트래픽 증가가 그 두 기능의 쿼터·요금에 함께 반영된다.
- 이 문서는 `global/translation/*`와 이를 소비하는 `domain/disasteralert`·`domain/event`의 번역 경로만 다룬다. 법정동 명칭 번역(`domain/legaldistrict`)은 FR-012에서 경계만 긋고 상세는 `specs/004-legal-district-matching/spec.md`에 위임한다.
- 프론트엔드의 언어 선택 상태(`languageStore`)와 `lang` 파라미터 전달 경로는 이 명세의 범위 밖이다 — 백엔드가 `lang`을 받은 시점부터를 다룬다.

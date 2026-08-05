# 기능 명세: 법정동(한국 행정구역) 지역 매칭

**기능 브랜치**: `004-legal-district-matching`

**생성일**: 2026-08-05

**상태**: Draft (as-built 문서화 — 이미 구현된 동작을 사후에 기술한 명세)

**입력**: 사용자 설명: "이미 구현된 법정동(한국 행정구역) 지역 매칭 서브시스템을 speckit 형식으로 as-built 문서화"

> **문서 성격 안내**: 이 문서는 신규 기능 요청이 아니라 `backend/.../domain/legaldistrict/*`와 그 소비처(알림, 관심지역, 이벤트 클러스터링, 위험도, 통계, 프론트엔드 지역 선택 UI)의 **실제 코드를 읽고 확인한 현재 동작**을 기술한다. 모든 사용자 스토리·인수 시나리오·기능 요구사항은 실제 구현에서 역으로 도출했으며, `file_path:line_number` 형태로 근거를 남긴다. 검증되지 않은 내용은 추측으로 채우지 않고 `[NEEDS CLARIFICATION]`으로 표시한다(헌법 원칙 IV, 정직한 문서화).

## 사용자 시나리오 및 테스트 *(필수)*

### 사용자 스토리 1 - 시/도 → 시/군/구를 선택해 관심지역을 등록한다 (우선순위: P1)

로그인한 사용자는 `/user/settings/regions` 화면에서 시/도를 먼저 고르고, 그 시/도에 속한 시/군/구 목록(서버가 다국어로 내려줌)에서 하나를 골라 관심지역으로 등록한다. 등록된 지역은 이후 해당 지역발 재난문자 푸시 알림의 대상이 된다.

**우선순위 이유**: 관심지역 등록은 알림 타겟팅(`AlertNotificationService`)의 입력 데이터이며, 이 서브시스템이 존재하는 가장 근본적인 이유다.

**독립 테스트 방법**: `GET /api/v1/districts/sigungu?sido=서울특별시`로 시군구 목록을 받아온 뒤 `POST` 관심지역 등록 API를 호출하고, `member_favorite_region`에 행이 생기는지 확인하면 완전히 검증 가능.

**인수 시나리오**:

1. **Given** 사용자가 시/도 드롭다운에서 "서울특별시"를 선택, **When** 프론트가 `GET /api/v1/districts/sigungu?sido=서울특별시&lang=ko`를 호출하면, **Then** 백엔드는 `legal_district` 테이블에서 `name LIKE '서울특별시 %'`인 행들의 시군구 부분(`SPLIT_PART(name, ' ', 2)`)을 distinct·정렬해 반환한다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/legaldistrict/repository/LegalDistrictRepository.java:26-34`).
2. **Given** 시군구 목록 응답, **When** 사용자가 "강남구"를 선택 후 등록 버튼을 누르면, **Then** 프론트는 `legalDistrictCode`(10자리 코드)를 등록 API에 전달하고, 서버는 코드 존재 여부를 확인한 뒤 `member_favorite_region(memberId, legalDistrictCode)`에 저장한다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/member/service/MemberFavoriteRegionService.java:34-47`).
3. **Given** 존재하지 않는 `legalDistrictCode`로 등록 요청, **When** 서버가 `legalDistrictRepository.findByCode()`로 조회, **Then** `LEGAL_DISTRICT_NOT_FOUND`(404, `LD404`) 예외를 던진다 (`MemberFavoriteRegionService.java:36-37`; `backend/src/main/java/com/disaster/alert/alertapi/domain/common/exception/ErrorCode.java:33`).

---

### 사용자 스토리 2 - 시/도 "전체"를 관심지역으로 등록하면 그 시/도 내 모든 시/군/구 알림을 받는다 (우선순위: P1)

특정 구가 아니라 시/도 전체를 관심지역으로 등록한 사용자/게스트도, 그 시/도 아무 시/군/구에서 발생한 재난문자 알림을 받는다.

**우선순위 이유**: `CLAUDE.md`에 명시된 핵심 지역 매칭 규칙이며, 알림 타겟팅에서 시군구 단순 일치가 아닌 시도 레벨 코드 파생이 필요한 이유다.

**독립 테스트 방법**: `2900000000`(광주광역시 전체)을 관심지역으로 등록한 회원이 있는 상태에서 시군구 레벨 코드(예: `2900101000`)를 지역으로 가진 재난문자를 트리거하면 해당 회원에게 알림이 가는지 확인.

**인수 시나리오**:

1. **Given** 시/군/구 드롭다운을 여는 사용자, **When** 서버가 시군구 목록을 만들 때, **Then** 첫 항목으로 `name="전체", code=<시도 10자리 코드>`가 항상 추가된다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/legaldistrict/service/LegalDistrictService.java:132-134`(한국어/미지원 lang 경로), `177-179`(번역 경로)).
2. **Given** 회원이 시도 전체 코드(예: `2900000000`)를 관심지역으로 등록한 상태, **When** `districtCode=2900101000`(광주광역시 특정 동)인 재난문자가 발송되면, **Then** `AlertNotificationService.triggerNotification`이 알림 지역코드에서 시도 레벨 코드(`code.substring(0,2) + "00000000"`)를 파생해 원본 코드와 합쳐(`allCodesToSearch`) `member_favorite_region`을 조회하므로 이 회원도 대상에 포함된다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/notification/service/AlertNotificationService.java:56-68`).
3. **Given** 위와 동일한 상황이지만 게스트 FCM 토큰이 시도 전체 코드로 등록된 경우, **When** 같은 재난문자가 발송되면, **Then** `sendToGuestTokens`가 동일한 `allCodesToSearch`로 `guest_fcm_region`을 조회해 게스트에게도 발송한다 (`AlertNotificationService.java:82,89-96`).

---

### 사용자 스토리 3 - 비로그인 게스트도 관심지역 기반 알림을 받을 수 있다 (우선순위: P2)

로그인하지 않은 사용자도 브라우저에 관심지역(최대 5개)과 FCM 토큰을 등록해 알림을 받을 수 있고, 이후 로그인하면 게스트 데이터가 서버 계정으로 자동 병합된다.

**우선순위 이유**: 회원가입 장벽 없이 알림 가치를 제공하기 위한 실제 배포된 경로(`fix/guest-fcm-token-permitall` 최근 커밋과 연관).

**독립 테스트 방법**: 비로그인 상태로 관심지역을 추가한 뒤 로그인하고, 서버의 `GET /favorite-regions` 응답에 게스트 지역이 반영되는지 확인.

**인수 시나리오**:

1. **Given** 비로그인 사용자가 관심지역 5개 미만 보유, **When** 새 지역을 추가하면, **Then** 서버 호출 없이 `zustand persist` 스토어(`disaster-alert-guest-favorites` localStorage 키)에 저장되고, 중복/5개 초과는 클라이언트에서 즉시 차단된다 (`frontend/src/store/guestFavoriteRegionsStore.ts:18-27`).
2. **Given** 게스트 관심지역이 1개 이상 있는 사용자가 로그인, **When** `useGuestFavoriteSync`가 실행되면, **Then** 서버에 이미 없는 코드만 골라(`serverCodes.has()` 필터) 순차적으로 등록 API를 호출하고, 개별 실패(한도 초과 등)는 무시한 뒤 로컬 스토어를 비운다 (`frontend/src/hooks/useGuestFavoriteSync.ts:21-53`).
3. **Given** 게스트 FCM 토큰이 로컬에 있는 상태에서 로그인, **When** 동기화가 실행되면, **Then** `linkGuestFcmToken`으로 토큰을 회원과 연결한다 (`useGuestFavoriteSync.ts:46-49`).

---

### 사용자 스토리 4 - 백엔드 파이프라인이 법정동 코드에서 시군구/시도 단위를 파생해 지역 그룹핑에 사용한다 (우선순위: P3)

최종 사용자에게 직접 보이지는 않지만, 이벤트 클러스터링·위험도 계산·통계 집계가 모두 법정동 10자리 코드에서 앞 5자리(시군구)·앞 2자리(시도)를 파생해 지역 단위 후보 필터링/그룹핑에 사용한다. 이 파생 로직은 운영자/개발자가 클러스터링·위험도·통계 결과의 지역 경계를 이해할 때 관찰되는 동작이다.

**우선순위 이유**: 최종 사용자에게 노출되는 알림·위험도·통계의 지역 정확도를 결정하는 내부 로직이라 P3로 낮췄지만, 코드베이스 전반에 동일 목적의 로직이 여러 곳에 독립적으로 구현되어 있어(아래 헌법 검사 참고) 문서화 가치가 크다.

**독립 테스트 방법**: `EventClusteringBackfillTool`로 과거 알림을 재처리하며 시군구 코드가 다른(예: 군 레벨 vs 읍면동 레벨) 두 알림이 같은 시군구 앞5자리로 매칭되는지 로그로 확인 가능.

**인수 시나리오**:

1. **Given** 신규 재난문자 알림, **When** `EventClusteringService`가 클러스터링 후보를 찾으면, **Then** 임베딩 유사도 비교 이전에 지역 hard 필터로 알림 지역코드들의 distinct 시군구 앞5자리 집합(`sigunguPrefixes`)과 교집합이 있는 기존 이벤트만 후보로 남긴다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/event/service/EventClusteringService.java:228-231,732-738`).
2. **Given** 한 알림이 임계치(`maxRegionSpan`)를 초과하는 시군구에 발송, **When** 광역 브로드캐스트로 분류되면, **Then** distinct 시도 수(`distinctSidoCount`, 앞2자리 기준)로 전국/시도광역을 나누고, 시도광역은 최다 시군구를 가진 시도 prefix + 유형을 병합 키로 쓴다 (`EventClusteringService.java:638-699`).
3. **Given** 이벤트의 위험도 재계산, **When** `RiskCalculationService.recomputeEventRisk`가 영향 법정동 union을 만들면, **Then** 그 코드들을 시군구 단위(앞 최대 5자리, `code.substring(0, Math.min(5, code.length()))`)로 축약해 반환하고, 이 시군구 코드가 `region_risk_index`/`region_risk_daily`의 지역 키가 된다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/risk/service/RiskCalculationService.java:124-146,157-179`).
4. **Given** 재난문자 시도별 통계 조회, **When** `DisasterAlertRepositoryImpl`이 그룹핑하면, **Then** QueryDSL `LEFT(code, 2)` 표현식으로 시도 코드를 뽑되, 세종특별자치시처럼 "코드 뒤 8자리가 0"인 관례를 따르지 않는 예외 때문에 표시명은 code prefix가 아니라 `legal_district.name`을 공백으로 split한 첫 토큰으로 별도 계산한다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/disasteralert/repository/DisasterAlertRepositoryImpl.java:376-396`).

---

### 사용자 스토리 5 - 법정동명을 다국어(EN/JA/ZH)로 번역해서 보여준다 (우선순위: P3)

사용자가 언어를 EN/JA/ZH로 바꾸면 시/군/구 이름이 번역되어 표시되고, 특정 언어 번역이 없으면 영어로, 영어도 없으면 한글 원본으로 대체된다.

**우선순위 이유**: 지역 매칭 자체의 핵심 로직은 아니지만 관심지역 등록 UX에 직접 노출되는 동작이며, `legaldistrict` 패키지가 소유한 별도 책임(`LegalDistrictTranslationService`)이다.

**독립 테스트 방법**: `GET /api/v1/districts/sigungu?sido=서울특별시&lang=ja` 호출로 일본어 번역이 있는 시군구는 일본어로, 없는 시군구는 영어로 내려오는지 확인.

**인수 시나리오**:

1. **Given** `lang=ko` 또는 미지원 값, **When** 시군구 목록을 조회하면, **Then** `translatedName`은 모두 `null`이며 추가 번역 쿼리를 하지 않는다 (`LegalDistrictService.java:104-139`).
2. **Given** `lang=ja`, **When** 특정 법정동에 JA 번역이 없으면, **Then** 같은 코드의 EN 번역으로 대체하고, EN도 없으면 결과 Map에서 제외해 호출측(프론트)이 한글 원본으로 대체하게 한다 (`backend/src/main/java/com/disaster/alert/alertapi/domain/legaldistrict/service/LegalDistrictTranslationService.java:87-115`).
3. **Given** 시군구 풀네임 번역(예: "Seoul Gangnam-gu"), **When** 시군구 목록 응답을 만들면, **Then** 시도 번역("Seoul") prefix를 제거해 "Gangnam-gu"만 반환하며, prefix가 매칭되지 않으면 풀네임을 그대로 반환하는 안전망을 둔다 (`LegalDistrictService.java:192-207`).

---

### 예외 상황

- 관심지역을 5개(ADMIN 제외) 초과 등록 시도 → `FAVORITE_REGION_LIMIT_EXCEEDED` (`MemberFavoriteRegionService.java:41-44`, 제한 상수 `USER_MAX_FAVORITE_REGION_COUNT=5`는 `MemberFavoriteRegionService.java:21`과 프론트 `guestFavoriteRegionsStore.ts:5`에 각각 독립적으로 하드코딩됨).
- 이미 등록된 지역 재등록 시도 → `FAVORITE_REGION_ALREADY_EXISTS` (`MemberFavoriteRegionService.java:38-40`).
- 존재하지 않는 법정동 코드로 관심지역/사용자 제보 지역 등록 시도 → `LEGAL_DISTRICT_NOT_FOUND`(관심지역, `MemberFavoriteRegionService.java:36-37`) 또는 `INVALID_REQUEST`(제보 지역, `backend/src/main/java/com/disaster/alert/alertapi/domain/useralert/service/UserDisasterAlertService.java:62-64,133-135` — `LegalDistrictCache.existsCode()` 사용).
- 행정구역 개편(2026-07-01, 광주광역시·전라남도 → 전남광주통합특별시 코드 재발급)으로 옛 코드가 폐지될 때 → 행을 삭제하지 않고 `is_active=false`로 전환(FK 무결성 보존). 과거 실측 데이터(재난문자 지역 태그 등)는 원칙적으로 개편 이전 옛 코드를 그대로 유지하지만, 예외적으로 재난문자/이벤트/위험도 이력 데이터는 이후 별도 마이그레이션으로 신규 코드에 전면 재매핑되었다 (`backend/src/main/resources/db/migration/V108__seed_jeonnam_gwangju_merged_legal_district.sql:1-5`, `V109__remap_favorite_and_weather_station_to_merged_codes.sql:1-7`, `V111__migrate_historical_alert_event_risk_data_to_merged_codes.sql:1-14`). 기상 관측 이력 테이블은 이 재매핑에서 제외된다.
- 프론트 `/user/settings/regions`의 지역 클릭 이동(`handleRegionClick`)은 `regionName` 문자열이 `METROS` 공식명/별칭으로 시작하지 않으면 `legalDistrictCode` 앞 2자리를 하드코딩된 `SIDO_BY_CODE_PREFIX` 맵으로 조회해 시도명을 역산하는데, 이 맵에는 신규 통합 코드 `"12"`(전남광주통합특별시) 키가 없다 — `regionName`이 "전남광주통합특별시"로 시작하는 1차 경로가 대부분 커버하지만, 만약 그 경로가 실패하는 데이터가 있으면 2차 폴백에서 `sido`가 `undefined`가 되어 `if (!sido) return;`으로 조용히 아무 동작도 하지 않는다 (알려진 잠재 결함) (`frontend/src/app/user/settings/regions/page.tsx:21-39,108-111`).

## 요구사항 *(필수)*

### 기능 요구사항

- **FR-001**: 시스템은 법정동 코드를 `legal_district(code VARCHAR(10) PK, name, is_active, is_active_string)` 테이블로 관리해야 한다(MUST) (`backend/src/main/java/com/disaster/alert/alertapi/domain/legaldistrict/model/LegalDistrict.java:6-24`; `backend/src/main/resources/db/migration/V1__create_schema.sql:4-10`).
- **FR-002**: 시스템은 법정동 코드의 1-2번째 자리를 시도, 3-5번째를 시군구, 6-8번째를 읍면동, 9-10번째를 리로 취급해야 한다(MUST) — 이 구조는 별도 검증 코드 없이 여러 소비처에서 고정 오프셋(`substring(0,2)`, `substring(0,5)`)으로 전제하고 있다 (`AlertNotificationService.java:59-60`, `EventClusteringService.java:682,735`, `RiskCalculationService.java:145`).
- **FR-003**: 시스템은 애플리케이션 초기화 시 classpath CSV(`data/legal_district_init_file.csv`, 헤더 제외 49,858건)로 법정동 마스터 데이터를 적재해야 한다(MUST)하며, 이미 존재하는 `code`는 건너뛴다(MUST) (`LegalDistrictService.java:33-74`).
- **FR-004**: 시/도 전체 선택("전체")은 시도 코드 뒤 8자리를 `0`으로 채운 10자리 코드로 표현해야 한다(MUST) (예: `2900000000`) (`LegalDistrictService.java:132-134,177-179`; `AlertNotificationService.java:57-60`).
- **FR-005**: 시스템은 회원 관심지역을 `member_favorite_region(memberId, legalDistrictCode)` 복합 PK로 저장해야 하며(MUST), 존재하지 않는 법정동 코드는 등록을 거부해야 한다(MUST) (`MemberFavoriteRegionService.java:34-47`; `ErrorCode.java:33`).
- **FR-006**: 시스템은 `USER` 역할 회원의 관심지역을 최대 5개로 제한하고, `ADMIN` 역할은 무제한 허용해야 한다(MUST) (`MemberFavoriteRegionService.java:21,41-44,49-54`).
- **FR-007**: 시스템은 동일 회원의 동일 지역 중복 등록을 거부해야 한다(MUST) (`MemberFavoriteRegionService.java:38-40`).
- **FR-008**: 시스템은 비로그인 게스트가 브라우저 localStorage(zustand `persist`, 키 `disaster-alert-guest-favorites`)에 관심지역을 최대 5개까지 등록할 수 있게 해야 하며(MUST), 5개 제한·중복 거부를 서버 호출 없이 클라이언트에서 동일하게 적용해야 한다(MUST) (`frontend/src/store/guestFavoriteRegionsStore.ts:5,18-27`).
- **FR-009**: 시스템은 로그인 성공 시 게스트 관심지역 중 서버에 없는 코드만 자동으로 서버에 등록해야 하며(MUST), 개별 등록 실패(예: 한도 초과)는 무시하고 나머지를 계속 처리해야 한다(MUST) (`frontend/src/hooks/useGuestFavoriteSync.ts:21-53`).
- **FR-010**: 재난문자 알림 발송 시 시스템은 알림의 지역코드 목록 중 10자리 코드에서 시도 레벨 코드(`code.substring(0,2) + "00000000"`)를 파생하여, 원본 지역코드와 합친 목록으로 관심지역 대상을 조회해야 한다(MUST) — 특정 시군구가 아닌 시도 전체를 등록한 회원도 대상에 포함시키기 위함이다 (`AlertNotificationService.java:56-68`).
- **FR-011**: 게스트 알림 대상 조회도 FR-010과 동일한 파생 지역코드 목록으로 `guest_fcm_region`을 조회해야 한다(MUST) (`AlertNotificationService.java:82,89-96`; `GuestFcmRegion.java:14-39`).
- **FR-012**: 이벤트 클러스터링은 신규 알림의 클러스터링 후보를 시군구(법정동 코드 앞 5자리) 교집합이 있는 기존 이벤트로 hard 필터링해야 한다(MUST), 읍면동 단위 코드 grain 차이를 흡수하기 위함이다 (`EventClusteringService.java:228-231,732-738`).
- **FR-013**: 지역앵커형 재난 유형(산불·산사태·홍수 등)과 안내성 알림의 클러스터링 병합 키는 시군구(앞 5자리)+유형(+윈도우)이어야 한다(MUST) (`EventClusteringService.java:437-503`).
- **FR-014**: 광역 브로드캐스트 알림(시군구 발송 수가 `maxRegionSpan` 초과)은 distinct 시도 수(코드 앞 2자리 기준)에 따라 전국(broadcast, 시도 무관 단일 키) 또는 시도광역(최다 시군구를 가진 시도 prefix+유형 키)으로 분류해야 한다(MUST) (`EventClusteringService.java:638-699`).
- **FR-015**: 위험도 계산은 이벤트 영향 법정동 코드를 시군구 단위(앞 최대 5자리)로 축약해 `region_risk_index`/`region_risk_daily`/`region_risk_history`의 지역 키로 사용해야 한다(MUST) (`RiskCalculationService.java:124-179`).
- **FR-016**: 재난문자 시도별 통계는 QueryDSL `LEFT(code, 2)`로 시도 코드를 추출하되, 표시명은 code prefix가 아닌 `legal_district.name`의 첫 공백 이전 토큰으로 별도 산출해야 한다(MUST) — 세종특별자치시 등 "코드 뒤 8자리가 0" 관례를 따르지 않는 예외 때문이다 (`DisasterAlertRepositoryImpl.java:376-396`).
- **FR-017**: 법정동 다국어 번역은 `legal_district_translation(code, language_code)` 복합 PK 테이블에 저장해야 한다(MUST). 요청 언어에 번역이 없으면 영어로 폴백해야 하며(MUST), 요청 언어가 한국어이거나 미지원 값이면 번역 조회 자체를 생략하고 `null`을 반환해야 한다(MUST) (`LegalDistrictTranslationService.java:49-115`; `LegalDistrictService.java:104-139`).
- **FR-018**: 시/군/구 다국어 목록 응답은 저장된 "시도+시군구" 풀네임 번역에서 시도 prefix를 제거해 시군구명만 반환해야 하며(MUST), prefix가 매칭되지 않으면 풀네임을 그대로 반환해야 한다(MUST) (`LegalDistrictService.java:192-207`).
- **FR-019**: 시/군/구 목록 API(`GET /api/v1/districts/sigungu`)는 응답 첫 항목으로 `{name: "전체", code: <시도 10자리 코드>}`를 포함해야 한다(MUST) (`LegalDistrictService.java:132-134,177-179`; `LegalDistrictController.java:33-39`).
- **FR-020**: 행정구역 개편으로 법정동 코드가 폐지될 때 시스템은 해당 행을 삭제하지 않고 `is_active=false`로 전환해야 한다(MUST)(과거 데이터의 FK 무결성 보존). 관심지역/기상관측소 매핑처럼 "미래 지향적" 참조는 신규 코드로 이관해야 하지만(MUST), 기상 관측 이력처럼 "기록 당시 실제 유효했던 코드"의 의미를 갖는 데이터는 원칙적으로 옛 코드를 유지해야 한다(MUST) (`V108__seed_jeonnam_gwangju_merged_legal_district.sql:1-5`; `V109__remap_favorite_and_weather_station_to_merged_codes.sql:1-30`; `V111__migrate_historical_alert_event_risk_data_to_merged_codes.sql:1-14`).
- **FR-021**: 사용자 제보(`UserDisasterAlert`)의 지역 태깅은 `LegalDistrictCache`(전체 법정동을 애플리케이션 메모리에 캐싱, `code`/`name` 양쪽 Map)로 코드 존재 여부만 검증하고 참조(`entityManager.getReference`)로 연결해야 한다(MUST) — DB 재조회 없이 lazy 참조만 생성한다 (`UserDisasterAlertService.java:57-67,122-138`; `backend/src/main/java/com/disaster/alert/alertapi/global/service/LegalDistrictCache.java:19-51`).

### 주요 엔티티

- **LegalDistrict**: 법정동 마스터. `code`(PK, 10자 문자열), `name`(전체 한글명, 예: "서울특별시 종로구 신교동"), `isActive`(존재/폐지), `isActiveString`(원본 CSV 문자열 "존재"/"폐지"). 다른 도메인은 이 엔티티를 FK로 참조하거나 `code` 문자열만 원시값으로 들고 다닌다.
- **LegalDistrictTranslation**: 법정동 코드별 언어별 번역명. PK=(`code`,`languageCode`). DeepL 실시간 호출 대신 캐시 성격의 시드 테이블.
- **MemberFavoriteRegion**: 회원-관심지역 매핑. PK=(`memberId`,`legalDistrictCode`). 회원당 최대 5개(ADMIN 무제한).
- **GuestFcmRegion**: 게스트(비로그인) FCM 토큰-관심지역 매핑. `id`(surrogate PK), `fcmToken`, `legalDistrictCode`.
- **(참조 소비처, 이 서브시스템이 소유하지 않음)**: `DisasterAlertRegion`(재난문자-지역 다대다), `UserDisasterAlertRegion`(사용자 제보-지역), `EventRegionImpact`(이벤트-영향지역), `RegionRiskIndex`/`RegionRiskDaily`/`RegionRiskHistory`(지역 키가 10자리가 아닌 5자리 시군구 코드 — FK 아님, 문자열로만 참조).

## 성공 기준 *(필수)*

### 측정 가능한 결과

- **SC-001**: 코드에 명시적으로 강제되는 유일한 정량 기준은 "회원 1인당 관심지역 5개"이며(ADMIN 예외), 이 숫자는 백엔드(`MemberFavoriteRegionService.java:21`)와 프론트엔드(`guestFavoriteRegionsStore.ts:5`)에 각각 독립된 상수로 하드코딩되어 있다(단일 소스가 아님).
- **SC-002**: 영어(EN) 법정동 번역 시드(V7)는 CSV 원본(49,858건, 헤더 제외)과 거의 동일한 규모(파일 49,866줄)로 시딩되어 있고, 일본어(V15)·중국어(V16) 시드도 각 49,872줄로 EN과 비슷한 규모다 — 파일 줄 수 기반 추정이며 실제 DB row 수(중복/충돌 제외 여부)는 확인하지 않았다. `LegalDistrictTranslationService.java:66-69`의 주석("현재 영어 시드만 적재돼 있어 JA/ZH 요청 시 한국어로 노출됐다")은 이 마이그레이션들 이후 시점 기준으로는 더 이상 정확하지 않을 가능성이 높다 — 실제 DB 조회로 재검증이 필요하다.
- **SC-003**: 응답 시간·처리량·가용성 등 운영 성능 지표(SLA)는 코드·설정 어디에도 정의되어 있지 않다 — 측정되지 않음.
- **SC-004**: 이 서브시스템(`legaldistrict` 패키지, `MemberFavoriteRegionService`, `AlertNotificationService`의 지역 매칭 부분)을 겨냥한 자동화된 단위/통합 테스트는 `backend/src/test`에서 발견되지 않았다 — 검증은 전적으로 코드 리딩에 의존했다.

## 가정

- 법정동 코드는 항상 10자리 고정 길이 문자열이라고 가정한다 — 여러 소비처가 `length()==10` 체크 후 고정 오프셋(`substring(0,2)`, `substring(0,5)`)을 사용한다 (`AlertNotificationService.java:59`, `EventClusteringService.java:682,735`, `RiskCalculationService.java:145`).
- CSV 시드 데이터에는 "리" 레벨(9-10번째 자리가 "00"이 아닌) 코드가 실제로 존재한다 — 전체 49,858건 중 36,497건(약 73%)이 마지막 2자리가 "00"이 아니다(부산 기장군 기장읍 동부리 `2671025021` 등, 직접 CSV를 카운트해 확인). 다만 재난문자(`disaster_alert_region`)가 실제로 이 리 레벨 코드로 태깅되는 사례가 있는지는 런타임 DB 데이터 없이는 코드만으로 확인할 수 없다. **[NEEDS CLARIFICATION: `disaster_alert_region.legal_district_code`의 실제 자리수/레벨 분포는 운영 DB 조회가 필요하며, 이 문서에서는 검증하지 못했다 — 리 레벨이 "죽은 코드"인지 실제 소비되는지 확정할 수 없다.]**
- 시도 "전체" 코드(앞2자리+"00000000")가 실제로 `legal_district`에 존재한다는 전제 하에, `AlertNotificationService`는 DB 조회 없이 문자열을 조합해서 사용한다(`AlertNotificationService.java:60`). 반면 `LegalDistrictService.getSigunguList()`는 같은 개념을 `findByName(sido)`로 실제 DB에서 조회해 확보한다(`LegalDistrictService.java:128-130,158-160`) — 같은 "시도 전체 코드"를 만드는 두 경로가 문자열 조합과 DB 조회로 서로 다르게 구현되어 있다.
- 세종특별자치시처럼 시군구 구분이 없는 시도가 "코드 뒤 8자리가 0" 관례의 유일한 예외로 코드 주석에 언급되어 있으나(`DisasterAlertRepositoryImpl.java:381-388`), 그 예외가 실제 CSV에서 어떤 코드 값으로 나타나는지는 별도로 조회하지 않았다.
- 프론트엔드 `SIDO_BY_CODE_PREFIX` 하드코딩 맵(`frontend/src/app/user/settings/regions/page.tsx:21-39`)은 2026-07-01 광주·전남 통합 개편으로 신설된 `"12"` 시도 코드를 포함하지 않는다 — `regionName` 문자열 기반 1차 매칭(`METROS`)이 대부분의 경우를 커버해 실사용 영향은 제한적으로 보이지만, 코드만으로는 100% 안전하다고 단정할 수 없다(알려진 결함, 예외 상황 절 참고).
- 이 문서는 `domain/legaldistrict/*`, 그리고 이를 소비하는 `notification`/`member`/`useralert`/`event`/`risk`/`disasteralert` 도메인의 지역 매칭 관련 코드 경로만 다룬다. `EventCrossRegionService`(실종자·탈출 동물 크로스 리전)는 법정동 코드 기반이 아니라 LLM 판정 기반이라 이 서브시스템의 의존처로 보지 않았다(코드에 `legaldistrict` 참조 없음, 확인함).

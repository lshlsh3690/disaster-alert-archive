# 구현 계획: 법정동(한국 행정구역) 지역 매칭

**브랜치**: `004-legal-district-matching` | **날짜**: 2026-08-05 | **명세**: [spec.md](./spec.md)

**입력**: `/specs/004-legal-district-matching/spec.md`의 기능 명세

**참고**: 이 문서 역시 as-built 문서화다 — 앞으로 구현할 계획이 아니라, `domain/legaldistrict/*`와 그 소비처가 **실제로 어떻게 구성되어 있는지**를 기록한다. "헌법 검사"는 사후 감사(audit)로 읽어야 한다.

## 요약

법정동(10자리 한국 행정구역 코드) 매칭은 `legaldistrict` 도메인 패키지가 마스터 데이터(`legal_district`)와 다국어 번역(`legal_district_translation`), 그리고 시/군/구 드롭다운 조회 API(`GET /api/v1/districts/sigungu`) 하나를 소유하는 얇은 서브시스템이다. 실제 "지역 매칭" 규칙(시도 전체 등록자 포함, 시군구 단위 클러스터링 hard 필터, 시군구 단위 위험도 집계, 시도 단위 통계 그룹핑)은 이 패키지 밖의 소비처(`notification`, `member`, `event`, `risk`, `disasteralert`)에 각각 독립적으로 구현되어 있고, "시도/시군구 코드를 앞 N자리로 파생한다"는 동일한 아이디어가 6개 지점(백엔드 5 + 프론트엔드 1)에서 서로 다른 방식(Java `substring`, QueryDSL `LEFT()`, 프론트 하드코딩 맵)으로 재구현되어 있다. 이 문서의 핵심 산출물은 그 중복 지점을 정확히 지도화하는 것이다.

## 기술 컨텍스트

**언어/버전**: 백엔드 Java 17+ / Spring Boot 3.x; 프론트엔드 TypeScript / Next.js(App Router)

**주요 의존성**: Spring Data JPA + QueryDSL(네이티브 쿼리 병행, `LegalDistrictRepository.findSigunguBySido`), Flyway(스키마+시드 마이그레이션), 프론트엔드 React Query(`useSigungu`), Zustand `persist`(게스트 관심지역 로컬 저장)

**저장소**: PostgreSQL — `legal_district`(마스터, 앱 시작 시 CSV 적재), `legal_district_translation`(EN/JA/ZH 시드), `member_favorite_region`, `guest_fcm_region`. 브라우저 `localStorage`(게스트 관심지역·FCM 토큰, 서버 저장소 아님).

**테스트**: 이 서브시스템 전용 단위/통합 테스트는 `backend/src/test`에서 발견되지 않음(확인함, `LegalDistrict`/`MemberFavoriteRegion` 키워드 검색 0건) — 헌법 원칙 III("검증 가능한 변경") 대비 실제 커버리지 공백.

**대상 플랫폼**: 웹(Spring Boot REST API + Next.js SSR/CSR), FCM 푸시(웹 서비스워커)

**성능 목표**: 코드/설정에 명시된 목표 없음 (spec.md SC-003 참고)

**제약사항**: 법정동 코드는 10자리 고정 문자열이라는 암묵적 전제(검증 코드 없음, spec.md 가정 참고). 행정구역 개편 시 코드는 삭제되지 않고 `is_active=false`로만 전환(V108~V111).

**규모/범위**: 법정동 마스터 약 49,858건, 번역 시드 언어당 유사 규모(EN/JA/ZH 3개 언어)

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

이 서브시스템은 이미 구현되어 운영 중이므로, 아래는 "게이트 통과 여부"가 아니라 **현재 코드가 각 원칙을 실제로 지키고 있는지에 대한 사후 감사**다.

- **원칙 I (가독성과 단순성 우선) — 부분 위반, 실제 중복 확인됨.**
  "법정동 코드에서 시도/시군구 상위 레벨을 파생한다"는 동일한 목적의 로직이 6개 지점(백엔드 5 + 프론트엔드 1)에서 각자 재구현되어 있고, `LegalDistrict` 엔티티나 `LegalDistrictService`에는 이를 위한 공용 헬퍼(예: `sidoCodeOf(code)`, `sigunguCodeOf(code)`)가 전혀 없다:
  1. `AlertNotificationService.java:60` — `code.substring(0,2) + "00000000"` (시도 10자리 코드 조합, 관심지역 매칭용)
  2. `EventClusteringService.java:682` (`sidoPrefix`) — `code.substring(0,2)` (2자리, 브로드캐스트 분류용)
  3. `EventClusteringService.java:735` (`sigunguPrefixes`) — `code.substring(0,5)` (5자리, 클러스터링 hard 필터용)
  4. `RiskCalculationService.java:145` — `code.substring(0, Math.min(5, code.length()))` (5자리, 위험도 지역 키용)
  5. `DisasterAlertRepositoryImpl.java:376-379`(`sidoCodeExpr`) — QueryDSL `function('left', code, 2)` (SQL 레벨 2자리, 통계 그룹핑용)
  6. (프론트) `frontend/src/app/user/settings/regions/page.tsx:21-39` — 시도 2자리 코드 → 시도명 하드코딩 맵(17개 항목), 백엔드 데이터와 무관한 별도 소스.

  각 사이트는 목적(시도 매칭 vs 시군구 매칭)과 언어(Java vs SQL)가 다르므로 "세 줄짜리 비슷한 코드는 섣부른 공통화보다 낫다"는 원칙 문구로 일부는 정당화될 수 있다. 그러나 **①과 ⑤는 같은 목적(시도 코드)·같은 언어(2자리 truncation)인데도 서로 다르게 구현**되어 있고, 실제로 ⑥(프론트 하드코딩 맵)은 2026-07-01 행정구역 개편(광주·전남 → 전남광주통합특별시, `12xx`) 이후에도 업데이트되지 않아 `"12"` 키가 누락된 채로 남아있다(spec.md 예외 상황 참고) — **중앙화되지 않은 파생 로직이 실제로 코드 드리프트(drift)를 만든 사례**로, 이 원칙이 방지하려는 문제가 실제로 발생했다고 봐야 한다.
  - **권고(문서화 목적, 이번 변경 범위 아님)**: `LegalDistrict` 또는 `LegalDistrictService`에 `sidoCode(String code)`/`sigunguCode(String code)` 정적 헬퍼를 두고 위 5개 백엔드 사이트를 교체하는 리팩터링을 별도 이슈로 고려할 만하다. 프론트 하드코딩 맵은 `GET /api/v1/districts/sigungu`가 이미 시도 코드를 내려주므로, 별도 상수 대신 API 응답을 캐시해 재사용하는 방향이 더 안전하다.

- **원칙 II (계층형 아키텍처 준수) — 부분 위반, 확인됨.**
  `LegalDistrictController → LegalDistrictService → LegalDistrictRepository` 3계층 분리, 예외의 `CustomException`+`ErrorCode`(`LEGAL_DISTRICT_NOT_FOUND` 등) 사용, DTO 설계(응답 형태별 단일 record, `SigunguResponse`)는 모두 준수한다. 그러나 `LegalDistrictController.getSigunguBySido`(`LegalDistrictController.java:33-39`)는 공용 `ApiResponse` 래퍼 없이 `ResponseEntity<List<SigunguResponse>>`를 직접 반환한다 — "모든 API 응답은 공용 ApiResponse/ApiErrorResponse 포맷을 사용"(MUST)이라는 헌법 원칙 II 문구에 대한 **확인된 위반**이다. 같은 서브시스템의 `MemberFavoriteRegionController`(`MemberFavoriteRegionController.java:26-55`)는 `GET`/`POST`/`DELETE` 3개 엔드포인트 전부를 `ApiResponse.success(...)`로 감싸고 있어, 이 위반이 코드베이스 전반의 관례가 아니라 `LegalDistrictController`에 고립된 결함임이 확인된다. spec.md 예외 상황 절에도 동일 내용을 남겼다.

- **원칙 III (검증 가능한 변경) — 위반(테스트 공백 확인).**
  `backend/src/test`에 `LegalDistrict*`/`MemberFavoriteRegion*` 관련 테스트가 전혀 없다. 시도 전체 코드 파생(FR-010), 시군구 hard 필터(FR-012), 위험도 지역 키 축약(FR-015) 같은 핵심 규칙이 전부 코드 리딩으로만 검증 가능한 상태다.

- **원칙 IV (정직한 문서화) — 이 문서 자체가 발견한 결함 2건.**
  1. `LegalDistrictTranslationService.java:66-69`의 주석이 "현재 영어 시드만 적재돼 있다"고 말하지만, `V15`/`V16` 마이그레이션(일본어/중국어)이 이미 EN과 비슷한 규모로 시딩되어 있어 주석이 최신 상태를 반영하지 못할 가능성이 높다(spec.md SC-002, 실제 DB 재검증 필요).
  2. 프론트 `SIDO_BY_CODE_PREFIX`(위 원칙 I)의 `"12"` 키 누락은 2026-07-01 개편 이후 발생한 실제 드리프트다.
  둘 다 이번 문서화 과정에서 숨기지 않고 spec.md에 남겼다.

- **원칙 V (한글 커밋 컨벤션)** — 이 문서화 작업 자체는 커밋/PR을 생성하지 않으므로 해당 없음.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/004-legal-district-matching/
├── plan.md              # 이 파일
└── spec.md              # 기능 명세 (as-built)
```
(`research.md`/`data-model.md`/`quickstart.md`/`contracts/`/`tasks.md`는 이미 구현이 끝난 기존 코드의 사후 문서화이므로 생성하지 않았다.)

### 소스 코드 (저장소 루트)

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── domain/legaldistrict/                       # 이 서브시스템이 직접 소유
│   ├── controller/LegalDistrictController.java # GET /api/v1/districts/sigungu
│   ├── service/LegalDistrictService.java       # CSV 적재 + 시군구 목록(번역 포함) 조합
│   ├── service/LegalDistrictTranslationService.java  # 코드→번역명 일괄 조회, EN 폴백
│   ├── repository/LegalDistrictRepository.java
│   ├── repository/LegalDistrictTranslationRepository.java
│   ├── model/LegalDistrict.java
│   ├── model/LegalDistrictTranslation.java
│   ├── model/LegalDistrictTranslationId.java
│   └── dto/SigunguResponse.java
├── global/service/LegalDistrictCache.java      # 전체 법정동 인메모리 캐시(name/code Map), useralert가 소비
├── domain/member/                              # 관심지역 소유(FK로 legaldistrict 참조)
│   ├── service/MemberFavoriteRegionService.java
│   ├── model/MemberFavoriteRegion.java, MemberFavoriteRegionId.java
│   └── repository/MemberFavoriteRegionRepository.java
├── domain/notification/service/AlertNotificationService.java   # 시도 레벨 코드 파생(회원+게스트 타겟팅)
├── domain/notification/model/GuestFcmRegion.java                # 게스트 지역-토큰 매핑
├── domain/event/service/EventClusteringService.java              # 시군구/시도 prefix 파생(클러스터링 hard 필터·브로드캐스트 분류)
├── domain/event/service/EventClusteringBackfillTool.java         # 현재 클러스터링 설정으로 과거 알림 재처리(수동 백필 도구, spec.md US4 독립 테스트 방법 참고)
├── domain/risk/service/RiskCalculationService.java                # 시군구 5자리 축약(위험도 지역 키)
├── domain/disasteralert/repository/DisasterAlertRepositoryImpl.java  # QueryDSL LEFT(code,2)(통계 그룹핑)
└── domain/useralert/service/UserDisasterAlertService.java         # LegalDistrictCache로 코드 존재 검증

backend/src/main/resources/
├── data/legal_district_init_file.csv            # 마스터 시드(런타임 적재, 49,858행)
└── db/migration/
    ├── V1__create_schema.sql                     # legal_district 테이블 생성
    ├── V6__create_legal_district_translation.sql
    ├── V7/V15/V16__seed_legal_district_translation_{en,ja,zh}.sql
    └── V108/V109/V111/V113__*jeonnam_gwangju*.sql # 2026-07-01 행정구역 개편 반영

frontend/src/
├── api/alertApi.ts                # fetchSigungu() → GET /api/v1/districts/sigungu
├── lib/queries/useAlerts.ts        # useSigungu() React Query 훅
├── store/guestFavoriteRegionsStore.ts   # 게스트 관심지역(zustand persist, localStorage)
├── hooks/useGuestFavoriteSync.ts   # 로그인 시 게스트→서버 병합
├── app/user/settings/regions/page.tsx   # 시/도→시/군/구 선택 UI, 관심지역 등록/삭제/이동
└── ui/metros.ts                    # 시/도 공식명 목록(METROS) + 구 표기 별칭
```

**구조 결정**: 표준 웹 애플리케이션 구조(`backend/` + `frontend/`)를 그대로 따른다. `legaldistrict` 패키지는 마스터 데이터/번역/드롭다운 조회만 소유하는 얇은 계층이고, "지역 매칭 규칙" 자체(시도 전체 포함, 시군구 hard 필터, 위험도 집계 단위)는 각 소비 도메인이 `code` 문자열을 받아 자체적으로 파생하는 구조다 — 이는 위 헌법 검사(원칙 I)에서 지적한 중복의 구조적 원인이기도 하다.

## 복잡도 추적

> **헌법 검사에서 위반 사항이 있어 정당화가 필요한 경우에만 작성**

| 위반 사항 | 필요한 이유 | 더 단순한 대안을 거부한 이유 |
|-----------|------------|-------------------------------------|
| 시도/시군구 코드 파생 로직이 6개 지점(Java 4곳 + QueryDSL 1곳 + 프론트엔드 1곳)에 중복 구현됨 (원칙 I) | 각 소비처가 요구하는 자리수(2 vs 5)와 실행 환경(JVM vs SQL vs 브라우저)이 달라, 최초 구현 시점마다 국지적으로 가장 빠른 방법을 택한 것으로 보인다(코드/커밋에 의도적 정당화 근거는 남아있지 않음 — 추정) | 이미 프로덕션에 배포되어 각기 다른 이력(클러스터링 임계값 튜닝, 위험도 전파, 통계 성능 최적화 주석 등)을 가진 코드라 이번 as-built 문서화 범위에서 일괄 리팩터링하지 않았다. 공용 헬퍼로의 통합은 별도 리팩터링 이슈로 분리하는 것을 권고한다(위 헌법 검사 원칙 I 참고). |
| 이 서브시스템에 전용 자동 테스트가 없음 (원칙 III) | 기존 코드베이스 전반의 테스트 커버리지가 낮다는 이미 알려진 상태(`CLAUDE.md`/`docs/TEST_CASE.md` 참고)의 연장선이다 | 이번 작업은 문서화이며 신규 테스트 작성은 범위 밖이다. 테스트 부재 자체를 숨기지 않고 spec.md SC-004에 명시했다. |

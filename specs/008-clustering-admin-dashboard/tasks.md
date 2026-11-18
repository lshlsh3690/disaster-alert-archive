---

description: "클러스터링 관리자 대시보드 작업 목록"
---

# 작업 목록: 클러스터링 관리자 대시보드

**입력**: `specs/008-clustering-admin-dashboard/`의 설계 문서

**전제조건**: plan.md, spec.md, research.md, data-model.md, contracts/admin-clustering-api.md, quickstart.md

**테스트**: 포함한다. CLAUDE.md 규칙대로 Red-Green 으로 진행한다 — Red 는 핵심 동작 2~4개만(경계값 테스트 금지), 로직 없는 뼈대(틀린 기본값)를 함께 둬서 **컴파일되고 실행되어 단언에서 실패**해야 하며, Red 를 커밋한 뒤 **사용자가 확인하기 전에는 Green 을 쓰지 않는다**(⛔ 표시). TDD 는 `tdd-developer` 를 `단계: RED`/`단계: GREEN` 으로 두 번 호출한다.

**구성**: 사용자 요청과 plan.md 구현 순서대로 층별(순수 계산 → 백엔드 API → 프론트 → 운영 확인)로 묶고, 각 작업에 해당 스토리를 붙인다. 스토리: US1 날짜별 운영 점수(P1), US2 불일치 쌍(P2), US3 구조 검사(P3), US4 판사 버전 이력(P3).

**범위 밖(태스크 없음)**: 쓰기 기능(채점 실행·라벨 수정·설정 변경), 차트, DB 마이그레이션, LLM 호출.

## 형식: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 의존성 없음)
- **[Story]**: 이 작업이 속한 사용자 스토리
- 경로 약어: `{main}` = `backend/src/main/java/com/disaster/alert/alertapi`, `{test}` = `backend/src/test/java/com/disaster/alert/alertapi`, `{fe}` = `frontend/src`

---

## Phase 1: 설정

**목적**: 새 설정·의존성은 없다. 작업 브랜치만 확인한다.

- [ ] T001 `feature/clustering-admin-dashboard`(develop 기준)에서 작업하는지 확인하고, `specs/008-clustering-admin-dashboard/` 문서(spec·plan·research·data-model·contracts·quickstart·tasks)가 커밋돼 있는지 확인한다

---

## Phase 2: 기반 작업 — 순수 계산 (plan 1·2절, 구현 순서 1·2)

**목적**: 네 스토리가 모두 쓰는 계산 규칙과 판사 버전 목록. JPA·Spring 을 import 하지 않는다.

- [ ] T002 [US4] `{main}/domain/event/judge/JudgeVersionCatalog.java` 뼈대를 만든다 — 버전 항목 record(버전, 모델, 같은 사건·다른 사건 일치율 분자/분모, 입력·출력 단가 USD/1M, 측정일)와 조회 메서드(`all()`, `find(version)`)를 두되 빈 목록·empty 를 돌려준다
- [ ] T003 [US1] `{main}/domain/event/dashboard/ClusteringDashboardMetrics.java` 뼈대를 만든다 — 비율 record(분자, 분모, 백분율 nullable), 날짜 상태 enum(`SCORED`/`NO_PAIRS`/`NO_RECORD`), 메서드 `ratio(num, den)`, `estimatedCostUsd(version, promptTokens, completionTokens)`, `fillDays(from, to, Map<LocalDate,?> records)`, `versionPeriods(List<(date, version)>)` 를 틀린 기본값으로
- [ ] T004 [US1] `{test}/domain/event/dashboard/ClusteringDashboardMetricsTest.java` 에 핵심 동작만 Red 로 쓴다(data-model.md "계산 값과 규칙"): (1) 분모 0 이면 백분율이 없고 0% 가 되지 않는다 (2) 요청 범위의 모든 날이 나오고 기록 없음·판정 0쌍·채점됨이 구별된다 (3) 추정 비용 = 토큰 × 그 버전 단가, 모르는 버전이면 비용 없음 (4) 버전별 첫날·마지막 날·채점 일수, 기록 없는 버전은 비어 있음
- [ ] T005 [P] [US4] `{test}/domain/event/judge/JudgeVersionCatalogTest.java` 에 핵심 동작만 Red 로 쓴다: v1 = gpt-4o·124/142·77/80, v2 = gpt-4o-mini·127/141·74/81 이고 최신 버전이 먼저 나온다
- [ ] T006 단언 실패를 확인하고(`./gradlew test --tests "*ClusteringDashboardMetricsTest" --tests "*JudgeVersionCatalogTest"`) 뼈대 커밋과 테스트 커밋을 나눠 `commit-message` 스킬로 커밋한다
- [ ] T007 ⛔ **멈춘다.** 사용자에게 테스트 파일과 실행 방법을 알리고 Red 확인을 기다린다
- [ ] T008 [US1] `ClusteringDashboardMetrics` 를 구현한다 — 맞게 묶은 비율·빠짐없이 묶은 비율·점수는 저장된 개수로 `{main}/domain/event/evaluation/PairCountResult` 를 만들어 그 메서드로 계산한다(R4, 정의 중복 금지). 단가는 `JudgeVersionCatalog` 에서 읽는다
- [ ] T009 [US4] `JudgeVersionCatalog` 를 구현하고, `{main}/domain/event/judge/ClusteringJudge.java` 의 `agreementOf`·`MODEL` 과 `{main}/domain/event/evaluation/JudgeAgreementReport.java` 의 단가 상수가 이 목록을 읽게 바꾼다(동작 동일, R2). `ClusteringJudgeTest`·`JudgeAgreementCounterTest`·`ClusteringDailyScoreServiceTest` 가 그대로 통과하는지 확인한다

**체크포인트**: 계산 규칙과 버전 목록이 한 곳에 있다. 판사·리포트 동작은 그대로다.

---

## Phase 3: 백엔드 조회 API (plan 3절, 구현 순서 3·4)

**목적**: `/api/v1/admin/clustering` 아래 네 엔드포인트와 ADMIN 권한(contracts/admin-clustering-api.md).

- [ ] T010 뼈대를 만든다: `{main}/domain/event/controller/ClusteringAdminController.java`(`@RequestMapping("/api/v1/admin/clustering")`, `@PreAuthorize("hasRole('ADMIN')")`, GET 4개), `{main}/domain/event/service/ClusteringDashboardService.java`(빈 응답), `{main}/domain/event/dto/` 응답 DTO 4개(계약의 필드명 그대로, 목록은 내부 중첩), `{main}/domain/common/exception/ErrorCode.java` 에 조회 범위 오류 코드
- [ ] T011 [US1] `{test}/domain/event/controller/ClusteringAdminControllerTest.java`(`@IntegrationTest`, 테스트 데이터는 테스트 안에서 넣고 롤백)에 Red 를 쓴다: (1) 일반 회원은 403, ADMIN 은 200 (2) `daily-scores` 가 범위의 모든 날을 내고 채점된 날은 계약대로 비율 분자/분모·판사 버전·당시 일치율을 담는다
- [ ] T012 [P] [US2] 같은 파일에 Red: `disagreements` 가 그날 OVER_MERGED·MISSED 쌍만 종류 필터·페이지로 내고, 쌍마다 두 알림 원문·발송 시각·유형·당시 이벤트 번호를 담는다(없는 알림은 원문 null)
- [ ] T013 [P] [US3] 같은 파일에 Red: `daily-checks` 가 날짜별 세 숫자와 `attention`(미배정 또는 임베딩 없음 > 0)을 낸다
- [ ] T014 [P] [US4] 같은 파일에 Red: `judge-versions` 가 버전 목록 값과 채점 기록의 첫날·마지막 날·일수를 낸다
- [ ] T015 단언 실패를 확인하고 뼈대 커밋과 테스트 커밋을 나눠 커밋한다(DB 가 필요한 통합 테스트 — 로컬 docker Postgres)
- [ ] T016 ⛔ **멈춘다.** 사용자 Red 확인을 기다린다
- [ ] T017 [US1] `ClusteringDailyScoreRepository` 에 범위 조회, `ClusteringDashboardService` 에 일일 채점 조회(범위 검증: 시작 ≤ 끝, 366일 이하, 위반 시 `CustomException`)와 `ClusteringDashboardMetrics` 조율을 구현한다
- [ ] T018 [US2] `ClusteringDailyScorePairRepository` 에 범위·종류 필터·페이지 조회를 더하고, `{main}/domain/disasteralert/service/DisasterAlertService.java` 에 "id 목록 → (id, 원문, 발송 시각, 유형)" 조회를 더해 그 service 를 거쳐 원문을 붙인다(R5, `event` 맥락이 알림 저장소를 직접 주입하지 않음, 한 페이지를 한 번에 조회)
- [ ] T019 [US3] `ClusteringDailyCheckRepository` 에 범위 조회를 더하고 구조 검사 조회를 구현한다
- [ ] T020 [US4] `ClusteringDailyScoreRepository` 에 버전별 첫날·마지막 날·일수 집계를 더하고 판사 버전 이력 조회를 구현한다
- [ ] T021 통합 테스트·기존 단위 테스트가 모두 통과하는지 확인한다. 로직 변경이 100줄 이상이면 `code-reviewer` 에 `git diff` 와 변경 줄 범위를 넣어 검토받고 결과를 사용자에게 보고한다

**체크포인트**: 네 API 가 계약대로 동작하고 ADMIN 만 접근한다.

---

## Phase 4: 프론트엔드 화면 (plan 4절, 구현 순서 5)

**목적**: `/admin/clustering` 한 페이지에 숫자 표 네 개.

- [ ] T022 [US1] 뼈대: `{fe}/api/clusteringAdminApi.ts`(axios 래퍼 4개, 계약의 DTO 타입 — 필드명 백엔드와 동일)와 `{fe}/lib/queries/useClusteringAdmin.ts`(튜플 키 예: `["admin", "clustering", "daily-scores", from, to]`)를 틀린 기본값으로
- [ ] T023 [US1] `{fe}/api/clusteringAdminApi.test.ts` 에 Red: 날짜 범위·필터·페이지가 쿼리 파라미터로 바르게 붙고 응답 `data` 를 꺼내 돌려준다(핵심 2개)
- [ ] T024 ⛔ 단언 실패를 확인하고 커밋한 뒤 **멈춘다.** 사용자 Red 확인을 기다린다
- [ ] T025 [US1] api·query 를 구현하고 `{fe}/app/admin/clustering/page.tsx` 를 만든다 — `authStore.role !== "ADMIN"` 이면 안내만(요청 없음), 판사 점수 설명 한 줄(spec FR-013), 일일 채점 표(기본 오늘 제외 최근 30일, 범위 선택, 비율은 `a/b (xx.x%)`, "계산할 수 없음"·"판정 0쌍"·"기록 없음" 구별, 같은 줄에 판사 버전·일치율, 추정 비용은 "상한" 표시)
- [ ] T026 [US2] 같은 페이지에 불일치 쌍 표를 더한다 — 날짜·종류 필터, 페이지 넘김, 두 알림 원문(없으면 "원문 없음")·발송 시각·유형·당시 이벤트 번호
- [ ] T027 [P] [US3] 같은 페이지에 구조 검사 표를 더한다 — `attention` 인 날 강조
- [ ] T028 [P] [US4] 같은 페이지에 판사 버전 이력 표를 더한다 — 기록 없는 버전은 "채점 기록 없음"
- [ ] T029 관리자에게만 보이는 진입 링크를 기존 사용자 메뉴에 하나 둔다(기존 메뉴 컴포넌트를 찾아 그 구조에 맞춤)
- [ ] T030 `npm test`·`npm run lint`·`npm run build` 를 통과하고, `ui-checker` 로 데스크톱·모바일 화면이 깨지지 않는지 확인한다

**체크포인트**: 관리자는 한 화면에서 네 영역을 보고, 일반 회원은 안내만 본다.

---

## Phase 5: 운영 확인 (구현 순서 6)

- [ ] T031 PR(develop 대상, `pr-message` 스킬)을 사용자 요청 시 연다. 푸시는 사용자가 요청할 때만 한다
- [ ] T032 ⛔ 머지·배포 뒤 사용자가 관리자 계정으로 `/admin/clustering` 을 열어 어제 점수와 판사 일치율을 찾을 수 있는지 확인한다(SC-001). 필요하면 quickstart.md 의 운영 DB 대조 SQL(읽기 전용)로 숫자를 맞춘다(SC-007)

---

## Phase 6: 마무리

- [ ] T033 [P] `specs/008-clustering-admin-dashboard/plan.md` 소스 구조 표를 실제 클래스 이름에 맞게 고친다(바뀐 경우)
- [ ] T034 [P] `CLAUDE.md` 의 이벤트 클러스터링 운영 검증 문장에 "관리자 화면 `/admin/clustering` 에서 본다"를 한 줄로 더하고 상세는 `specs/008-clustering-admin-dashboard/` 를 가리킨다(수치를 다시 쓰지 않음)

---

## 의존성 및 실행 순서

### Phase 의존성

- **Phase 2(순수 계산)**: 바로 시작. Phase 3 의 service 가 계산 클래스를 쓰므로 먼저 끝나야 한다(T008·T009 → T017~T020)
- **Phase 3(백엔드 API)**: Phase 2 Green 이후. Phase 4 가 계약대로 만든 API 를 부른다
- **Phase 4(프론트)**: 계약(contracts/)만 있으면 뼈대·api 테스트(T022~T023)는 Phase 3 와 병렬 가능. 화면 연동 확인(T030)은 Phase 3 Green 이후
- **Phase 5·6**: Phase 4 이후

### 스토리별

- US1(일일 채점)이 MVP — T003·T004·T008·T011·T017·T022~T025
- US2·US3·US4 는 US1 의 페이지·컨트롤러에 영역을 더하는 식이라 US1 이후 서로 독립

### 병렬 기회

- T004 와 T005(테스트 파일이 다름)
- T012·T013·T014(같은 파일이지만 서로 다른 테스트 메서드 — 한 번의 RED 호출에서 같이 쓴다)
- T027·T028(같은 페이지의 다른 영역 — 순서만 정하면 됨)
- T033·T034

### 예: Phase 2 Red 를 한 번에

```text
tdd-developer 단계: RED — T002·T003 뼈대 + T004·T005 테스트, 뼈대/테스트 커밋 분리 후 멈춤
→ 사용자 "Red 확인" → tdd-developer 단계: GREEN — T008·T009
```

## 구현 전략

### MVP 먼저 (US1)

1. Phase 2 → Phase 3 의 US1 부분(T010·T011·T017) → Phase 4 의 US1 부분(T022~T025)
2. 관리자 화면에서 날짜별 점수와 판사 일치율이 보이면 MVP 완료

### 점진 전달

- US2(불일치 쌍) → US3(구조 검사) → US4(버전 이력) 순으로 영역을 더한다. 각 영역은 API 한 개 + 표 한 개라 따로 확인할 수 있다
- 푸시·CodeRabbit 요청은 작업 단위 끝에 한 번만, 사용자가 요청할 때

---

description: "불일치 쌍 사람 라벨링 작업 목록"
---

# 작업 목록: 불일치 쌍 사람 라벨링

**입력**: `specs/009-clustering-pair-labeling/`의 설계 문서

**전제조건**: plan.md, spec.md, research.md, data-model.md, contracts/labeling-api.md, quickstart.md

**테스트**: 포함한다. CLAUDE.md 규칙대로 Red-Green 으로 진행한다 — Red 는 핵심 동작 2~4개만(경계값 테스트 금지), 로직 없는 뼈대(틀린 기본값)를 함께 둬서 **컴파일되고 실행되어 단언에서 실패**해야 하며, Red 를 커밋한 뒤 **사용자가 확인하기 전에는 Green 을 쓰지 않는다**(⛔ 표시). TDD 는 `tdd-developer` 를 `단계: RED`/`단계: GREEN` 으로 두 번 호출한다.

**구성**: plan.md 구현 순서대로 층별(순수 판정·집계 → 백엔드 → 프론트 → 문서·운영)로 묶고, 각 작업에 해당 스토리를 붙인다. 스토리: US1 라벨 저장(P1), US2 어려운 쌍 판사 성적(P2), US3 평가 도구가 DB 라벨을 회귀 셋으로 읽음(P3). 2026-11-18 사용자 결정으로 파일 내보내기는 없앴다(research R8).

**범위 밖(태스크 없음)**: 라벨 파일 내보내기, 운영 이벤트 자동 수정, 임계값·규칙 자동 조정, 판사 프롬프트 자동 수정, 판사 판정으로 라벨 미리 채우기, 라벨 변경 이력, LLM 호출.

## 형식: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 의존성 없음)
- **[Story]**: 이 작업이 속한 사용자 스토리
- 경로 약어: `{main}` = `backend/src/main/java/com/disaster/alert/alertapi`, `{test}` = `backend/src/test/java/com/disaster/alert/alertapi`, `{fe}` = `frontend/src`, `{mig}` = `backend/src/main/resources/db/migration`

---

## Phase 1: 설정

**목적**: 새 의존성은 없다. 작업 브랜치와 문서만 확인한다.

- [ ] T001 `feature/clustering-dashboard-labeling`(develop 기준)에서 작업하는지 확인하고, `specs/009-clustering-pair-labeling/` 문서(spec·plan·research·data-model·contracts·quickstart·tasks)가 커밋돼 있는지 확인한다

---

## Phase 2: 기반 작업 — 순수 판정·집계 (plan 2절, 구현 순서 1·2)

**목적**: 세 스토리가 함께 쓰는 "누가 틀렸는지" 규칙, 판사 버전별 집계, 평가용 라벨 묶음, 기준 시점 상수. JPA·Spring 을 import 하지 않는다.

- [ ] T002 [US1] `{main}/domain/event/evaluation/ClusteringCriteria.java` 뼈대를 만든다 — `VERSION_DATE`(틀린 기본값, 예: `LocalDate.MIN`). javadoc 에 "`CLUSTERING_CRITERIA.md` 를 바꾸면 이 값도 바꾼다"를 적는다
- [ ] T003 [US2] `{main}/domain/event/dashboard/DisagreementGrading.java` 뼈대를 만든다 — `enum WrongSide{JUDGE,SYSTEM}`, `wrongSide(label, judgeVerdict, systemSameEvent)`(항상 null), `gradeByVersion(List<GradedPair>)`(빈 결과). 입력·출력 record 는 data-model.md 그대로. 비율은 `ClusteringDashboardMetrics.Ratio` 를 쓴다
- [ ] T004 [US2] `{test}/domain/event/dashboard/DisagreementGradingTest.java` 에 Red: (1) data-model.md 표의 네 경우 중 판사가 틀린 경우·시스템이 틀린 경우 각 하나와 UNSURE → null, (2) spec 스토리 2 예시(같은 사건 3·다른 사건 1·모르겠음 1)로 버전별 집계가 판사 2/4·시스템 2/4·모르겠음 1. 핵심 2~3개만
- [ ] T005 [P] [US3] `{main}/domain/event/evaluation/PairLabelSnapshot.java` 뼈대(`select(rows, asOf)` 가 빈 목록)와 `{test}/domain/event/evaluation/PairLabelSnapshotTest.java` Red: 기준 시각 뒤에 고친 행은 빠지고 결과가 `(alert_a, alert_b)` 순이다. 1개만
- [ ] T006 단언 실패를 확인하고(`./gradlew test --tests "*DisagreementGradingTest" --tests "*PairLabelSnapshotTest"`) 뼈대 커밋과 테스트 커밋을 나눠 커밋한다(commit-message 스킬)
- [ ] T007 ⛔ **멈춘다.** 사용자에게 테스트 파일과 실행 방법을 알리고 Red 확인을 기다린다
- [ ] T008 [US2] `DisagreementGrading` 을 구현한다 — data-model.md 규칙(판사 맞음 ⇔ 판정==라벨, 시스템 맞음 ⇔ 같은 이벤트==(라벨==SAME)), 버전별 집계(분모에서 UNSURE 제외, `JudgeVersionCatalog` 순서). `PairLabelSnapshot.select` 를 구현한다(기준 시각 이하·정렬)
- [ ] T009 [US1] `ClusteringCriteria.VERSION_DATE` 를 `2026-11-13` 으로 둔다

**체크포인트**: 순수 판정·집계·라벨 묶음 테스트 통과.

---

## Phase 3: 백엔드 — 저장·조회 API (plan 1·3·4절, 구현 순서 3·4)

**목적**: 라벨 테이블과 엔드포인트. ADMIN 전용, `ApiResponse`, `CustomException`+`ErrorCode`.

- [ ] T010 뼈대를 만든다: `{mig}/V140__create_clustering_pair_label.sql`(data-model.md 표 그대로, `UNIQUE(alert_a, alert_b)`, `CHECK(alert_a < alert_b)`, label CHECK), `{main}/domain/event/model/ClusteringPairLabel.java`(엔티티 + `relabel` 빈 몸체), `{main}/domain/event/repository/ClusteringPairLabelRepository.java`, `{main}/domain/event/service/ClusteringPairLabelService.java`(아무것도 저장하지 않음), `{main}/domain/event/controller/ClusteringAdminController.java` 에 엔드포인트 2개(`PUT /disagreement-labels`, `GET /hard-pair-grades`)와 `disagreements` 의 `unlabeledOnly` 파라미터, 요청·응답 DTO(`{main}/domain/event/dto/`), 불일치 쌍 응답의 `humanLabel`·`criteriaDate`·`wrongSide` 필드(항상 null), `{main}/domain/common/exception/ErrorCode.java` 에 `CLUSTERING_PAIR_NOT_FOUND`(404)
- [ ] T011 [US1] `{test}/domain/event/controller/ClusteringAdminLabelingTest.java`(`@IntegrationTest` + MockMvc, 008 `ClusteringAdminControllerTest` 와 같은 테스트 데이터 방식)에 Red: (1) 관리자가 불일치 쌍에 라벨을 저장하고 다시 저장하면 한 행만 남고 마지막 라벨·기준 시점이 보인다, (2) 일반 회원은 403 이고 행이 생기지 않는다, (3) 불일치로 기록되지 않은 쌍은 404
- [ ] T012 [P] [US1] 같은 파일에 Red: `disagreements` 응답에 라벨·`wrongSide` 가 붙고, `unlabeledOnly=true` 면 라벨된 쌍이 빠진다
- [ ] T013 [P] [US2] 같은 파일에 Red: `hard-pair-grades` 가 판사 버전별 판사 맞음·시스템 맞음(분자/분모)·모르겠음 수를 돌려준다
- [ ] T014 단언 실패를 확인하고 뼈대 커밋과 테스트 커밋을 나눠 커밋한다
- [ ] T015 ⛔ **멈춘다.** 사용자 Red 확인을 기다린다
- [ ] T016 [US1] `ClusteringPairLabel.relabel`·`ClusteringPairLabelService` 를 구현한다 — 쌍 순서 맞추기, 불일치 쌍 존재 확인(없으면 `CustomException(CLUSTERING_PAIR_NOT_FOUND)`), 찾으면 `relabel`·없으면 새로 저장, UNIQUE 충돌 시 다시 찾아 `relabel`(research R4). 회원 id 는 컨트롤러가 `@AuthenticationPrincipal(expression = "id")` 로 넘긴다
- [ ] T017 [US1] `{main}/domain/event/repository/ClusteringDailyScorePairRepository.java` 에 라벨 없는 쌍만 거르는 조회를 더하고, `{main}/domain/event/service/ClusteringDashboardService.java` 의 `disagreements` 가 라벨을 한 페이지분 한 번에 읽어 `humanLabel`·`criteriaDate`·`wrongSide`(`DisagreementGrading.wrongSide`)를 채우게 한다
- [ ] T018 [US2] `ClusteringDashboardService` 에 `hardPairGrades` 를 구현한다 — 지금 불일치인 쌍 중 라벨된 것과 그날 판사 버전을 읽어 `DisagreementGrading.gradeByVersion` 에 넘긴다
- [ ] T019 [US3] `{main}/domain/event/evaluation/EvaluationRunTool.java` 에 `--eval.pair-labels-as-of=<ISO 시각>` 을 더한다 — `--eval.labels` 와 함께 주면 오류, `ClusteringPairLabelRepository` 로 라벨 행을 읽어 `PairLabelSnapshot.select` 로 목록을 만들고, 0개면 오류로 끝내며, 데이터셋 이름은 `불일치 쌍 사람 라벨(DB, 기준 …, N쌍)`. 이후 채점·리포트는 파일 모드와 같다. `backend/evaluation/README.md` 클러스터링 평가 절에 실행 예시를 더한다(contracts 4절)
- [ ] T020 통합 테스트·기존 단위 테스트가 모두 통과하는지 확인한다. 로직 변경이 100줄 이상이면 `code-reviewer` 에 `git diff` 와 변경 줄 범위를 넣어 검토받고 결과를 사용자에게 보고한다

**체크포인트**: 관리자가 API 로 라벨을 저장·수정하고, 불일치 쌍·성적에 반영되며, 평가 도구가 DB 라벨로 채점한다.

---

## Phase 4: 프론트엔드 (plan 5절, 구현 순서 5)

**목적**: 008 화면에 라벨 버튼·필터·성적 표를 더한다.

- [ ] T021 ⛔ 사용자에게 이번 프론트엔드를 TDD(Red 확인)로 할지 바로 구현할지 묻는다(008 은 사용자 지시로 생략했다). TDD 면 `{fe}/api/clusteringAdminApi.test.ts` 에 라벨 저장 요청 Red 를 쓰고 멈춘다
- [ ] T022 [US1] `{fe}/api/clusteringAdminApi.ts` 에 `saveDisagreementLabel`·`fetchHardPairGrades` 와 계약의 타입(불일치 쌍의 `humanLabel`·`criteriaDate`·`wrongSide`, `unlabeledOnly`)을 더한다. 요청에 `X-Auth-Required` 헤더
- [ ] T023 [US1] `{fe}/lib/mutations/useSaveDisagreementLabel.ts` 를 만든다 — 낙관적 업데이트 없이, 성공하면 `["admin","clustering","disagreements"]`·`["admin","clustering","hard-pair-grades"]` 를 무효화
- [ ] T024 [US1] `{fe}/app/admin/clustering/_sections.tsx` 불일치 쌍 표에 같은 사건/다른 사건/모르겠음 버튼(현재 라벨 강조, 저장 중 비활성, 실패 시 이유 표시), "판사가 틀림"/"시스템이 틀림" 표시, "라벨 없는 쌍만" 체크를 더한다
- [ ] T025 [P] [US2] `{fe}/lib/queries/useClusteringAdmin.ts` 에 성적 훅을 더하고, `_sections.tsx` 판사 버전 이력 아래에 "어려운 쌍(불일치 쌍 사람 라벨 기준) 판사 성적" 표를 둔다. 라벨이 없으면 "아직 라벨 없음", 272쌍 일치율과 다른 숫자임을 한 줄로 밝힌다
- [ ] T026 `npm test`·`npm run lint`·`npm run build` 를 통과하고, `ui-checker` 로 데스크톱·모바일에서 버튼·표가 깨지지 않는지 확인한다

**체크포인트**: 관리자가 화면에서 라벨을 매기고, 성적·누가 틀렸는지가 보인다.

---

## Phase 5: 문서 (구현 순서 6)

- [ ] T027 [P] `specs/008-clustering-admin-dashboard/spec.md` FR-011 을 "쓰기는 사람 라벨 저장만 허용(009)"으로 고친다
- [ ] T028 [P] `specs/001-event-clustering-pipeline/CLUSTERING_CRITERIA.md` 머리에 "기준을 바꾸면 `ClusteringCriteria.VERSION_DATE` 도 바꾼다(불일치 쌍 라벨의 기준 시점)" 한 줄을 더한다
- [ ] T029 [P] `specs/009-clustering-pair-labeling/plan.md` 소스 구조 표를 실제 클래스 이름에 맞게 고친다(바뀐 경우)

---

## Phase 6: 운영 확인 (구현 순서 7)

- [ ] T030 PR(develop 대상, `pr-message` 스킬)을 사용자 요청 시 연다. 푸시는 사용자가 요청할 때만 한다
- [ ] T031 ⛔ 머지·배포 뒤 운영에 불일치 쌍이 생기면 사용자가 관리자 화면에서 라벨을 매기고, 새로고침 후 유지·성적을 확인하고, 운영 덤프로 만든 평가 사본에서 DB 라벨 모드 평가를 돌려 본다(quickstart 시나리오 2~4). 운영 DB 확인은 읽기 전용

---

## 의존성 및 실행 순서

### Phase 의존성

- **Phase 2(순수)**: 바로 시작. Phase 3 service 가 판정·집계를 쓰므로 먼저 끝나야 한다(T008·T009 → T017~T019)
- **Phase 3(백엔드)**: Phase 2 Green 이후. T016(저장)이 T017~T019 보다 먼저
- **Phase 4(프론트)**: T021 사용자 결정 뒤. 화면 연동 확인(T026)은 Phase 3 Green 이후
- **Phase 5**: 언제든(문서만). Phase 6 은 Phase 4 이후

### 스토리별

- US1(라벨 저장)이 MVP — T002·T009·T010·T011·T012·T016·T017·T022~T024
- US2(성적)·US3(DB 라벨 평가)는 US1 의 라벨 데이터 위에 올라가며 서로 독립

### 병렬 기회

- T004·T005(같은 파일, 한 번의 RED 호출에서 같이 쓴다)
- T011~T013(같은 파일의 서로 다른 테스트 메서드 — 한 번의 RED 호출)
- T027·T028·T029

### 예: Phase 2 Red 를 한 번에

```text
tdd-developer 단계: RED — T002·T003 뼈대 + T004·T005 테스트, 뼈대/테스트 커밋 분리 후 멈춤
→ 사용자 "Red 확인" → tdd-developer 단계: GREEN — T008·T009
```

## 구현 전략

### MVP 먼저 (US1)

1. Phase 2 → Phase 3 의 US1 부분(T010·T011·T012·T016·T017) → Phase 4 의 US1 부분(T022~T024)
2. 관리자가 화면에서 라벨을 매기고 새로고침해도 남으면 MVP 완료

### 점진 전달

- US2(판사 성적) → US3(평가 도구 DB 라벨 모드) 순으로 더한다. 성적은 API·화면 영역 하나, DB 라벨 모드는 평가 도구 옵션 하나라 따로 확인할 수 있다
- 푸시·CodeRabbit 요청은 작업 단위 끝에 한 번만, 사용자가 요청할 때

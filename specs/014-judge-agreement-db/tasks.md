# 작업 목록: 판사 일치율 측정 결과를 DB 에 두고 현재 라벨로 계산한다

**입력**: `specs/014-judge-agreement-db/`의 설계 문서(plan.md, spec.md, research.md, data-model.md, contracts/judge-agreement.md, quickstart.md)

**전제조건**: PR #358(라벨 JSON 읽기 삭제)이 develop 에 머지돼 있어야 한다. 로컬 Postgres·Redis 가 떠 있어야 통합 테스트가 돈다.

**테스트**: 저장소 규칙(CLAUDE.md)대로 새 순수 로직은 Red-Green 으로 한다. Red 는 `tdd-developer`(단계: RED)가 쓰고 커밋한 뒤 **사용자가 확인할 때까지 멈춘다**. Green 은 사용자가 "Red 확인했다/Green 진행해"라고 한 뒤에만 한다. Red 는 핵심 동작 2~4개만 둔다.

**판사(유료) 호출**: 어떤 작업도 판사를 실제로 부르지 않는다.

## 형식: `[ID] [P?] [Story] 설명`

- **[P]**: 병렬 실행 가능(다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: 사용자 스토리(US1, US2, US3)
- 경로: 백엔드 `backend/src/main/java/com/disaster/alert/alertapi/domain/event/` 를 `{event}` 로, 테스트 `backend/src/test/java/com/disaster/alert/alertapi/domain/event/` 를 `{event-test}` 로 줄여 쓴다.

---

## Phase 1: 설정

**목적**: 선행 PR 반영과 시드 원자료 확보

- [x] T001 PR #358 머지 여부를 확인하고 `feature/judge-agreement-db` 를 최신 develop 위로 옮긴다(미커밋 명세 파일은 그대로 유지). 머지 전이면 여기서 멈추고 사용자에게 알린다.
- [x] T002 로컬 사본 `disaster_alert_prod`(272쌍 라벨 있음)에서 research R4 의 일회성 SQL 로 v2 쌍별 225행을 뽑는다 — 리포트 `backend/evaluation/reports/judge-agreement-2026-11-18-mini.md` 의 `#### A ↔ B · 사람 X · 판사 Y` 24줄을 VALUES 로 넣고, 라벨(UNSURE 제외)과 `LEAST/GREATEST` 로 맞춰 `COALESCE(listed.verdict, label)`. 결과를 scratchpad 에 저장하고 분포가 SAME 134 · DIFF 88 · UNSURE 3, 교차표가 127/14/3/74/7 인지 확인한다. 저장소에 스크립트 파일을 남기지 않는다.

---

## Phase 2: 기반 작업 (모든 스토리 선행)

**목적**: 테이블·엔티티와 일치율 계산 규칙

- [x] T003 `backend/src/main/resources/db/migration/V145__create_judge_agreement_run.sql` 를 data-model 1·2절대로 만든다(두 테이블, CHECK 3개, UNIQUE, 인덱스 `(judge_version, finished_at DESC, id DESC)`, 알림 외래 키 없음). 파일 머리 주석에 011 에서 가져온 것·뺀 것(research R1)을 적는다.
- [x] T004 [P] `{event}/model/JudgeAgreementRun.java` 엔티티 — 생성 팩터리 두 개(쌍별 판정 있는 측정 / 합계만 있는 측정)만 두고 수정 메서드는 두지 않는다(FR-012).
- [x] T005 [P] `{event}/model/JudgeAgreementRunPair.java` 엔티티 — 생성 때 `alert_a < alert_b` 로 정규화, 판정은 `SAME/DIFF/UNSURE/FORMAT_FAILURE/CALL_FAILURE`(호출 실패는 `JudgeVerdict` 에 넣지 않음, data-model 2절).
- [x] T006 [P] `{event}/repository/JudgeAgreementRunRepository.java`(버전별 최신 측정 1건 조회), `{event}/repository/JudgeAgreementRunPairRepository.java`(run_id 로 전부 조회). `{event}/repository/EvaluationLabelRepository.java` 의 기존 `findByDatasetOrderByIdAsc` 를 쓰고 새 메서드가 필요 없으면 손대지 않는다.
- [x] T007 `tdd-developer`(단계: RED)로 `{event-test}/judge/JudgeAgreementCalculatorTest.java` 를 쓴다. 핵심 동작 4개: (1) 현재 라벨로 같은 사건·다른 사건 분자/분모를 센다, (2) 라벨이 SAME→DIFF 로 바뀐 쌍은 다른 사건 분모로 옮겨 간다, (3) 라벨은 있는데 판정이 없는 쌍은 분모 밖에서 `unmeasuredPairs` 로 센다, (4) 판사 UNSURE·형식 실패·호출 실패는 분모 밖에서 따로 센다. 틀린 기본값을 돌려주는 뼈대 `{event}/judge/JudgeAgreementCalculator.java`(JPA·Spring import 없음)를 함께 두고 커밋한다.

**체크포인트(멈춤)**: 사용자에게 `./gradlew test --tests "*JudgeAgreementCalculatorTest"` 로 Red 확인을 요청한다. 명시적 확인 전에는 T008 을 하지 않는다.

- [x] T008 `tdd-developer`(단계: GREEN, 사용자 Red 확인 명시)로 `{event}/judge/JudgeAgreementCalculator.java` 를 구현한다. 규칙은 data-model 4절·기존 `JudgeAgreementCounter` 와 같다. 테스트 파일은 고치지 않는다.

---

## Phase 3: 사용자 스토리 1 — 판사 일치율이 DB 의 측정 기록에서 나온다 (P1) 🎯 MVP

**목표**: 대시보드 판사 버전 이력과 일일 채점의 일치율이 코드 상수가 아니라 DB 측정 기록 × 현재 라벨에서 나온다.

**독립 테스트**: 라벨 있는 로컬 사본에서 판사 버전 이력이 v2 127/141 · 74/81, v1 124/142 · 77/80(합계만)으로 보인다(quickstart 2·3절).

- [x] T009 [US1] `backend/src/main/resources/db/migration/V146__seed_judge_agreement_v1_v2.sql` — v1 합계 행, v2 측정 행 + T002 의 225행(고정 VALUES, `evaluation_pair_label` 을 읽지 않음). `run_id` 는 서브쿼리로 연결한다. 머리 주석에 출처(리포트 파일·커밋 c2ecbfa1·8ad95f19), 측정 시각·토큰·비용, v2 리포트 머리의 "v1" 표기 차이(research R6)를 적는다.
- [x] T010 [US1] `{event}/service/JudgeAgreementService.java` — `Optional<VersionAgreement> latest(String version)`: 최신 측정을 읽고, 쌍별 판정이 있으면 그 `dataset` 라벨을 읽어 계산기에 넘기고(라벨 0행이면 empty), 없으면 legacy 합계를 `recomputable=false` 로 돌려준다. 규칙은 계산기에만 둔다(헌법 VI).
- [x] T011 [US1] `{event}/service/ClusteringDailyScoreService.java` 의 `score` 가 `ClusteringJudge.agreementOf` 대신 `JudgeAgreementService.latest(PROMPT_VERSION)` 를 쓰게 한다. 비면 지금처럼 판사를 부르지 않고 warn 후 반환한다(FR-007). 저장 형식(`agreement_*` 4열)은 그대로다.
- [x] T012 [US1] `{event}/dto/ClusteringDashboardJudgeVersionsResponse.java` 에 `Boolean recomputable`, `Integer unmeasuredPairs` 를 더하고, `{event}/service/ClusteringDashboardService.java` 의 `judgeVersions` 가 카탈로그 순서로 돌며 서비스 값을 붙이게 한다. 측정이 없거나 계산할 수 없는 버전은 측정 관련 필드를 null 로 둔다(contracts 1절).
- [x] T013 [US1] 상수 제거: `{event}/judge/JudgeVersionCatalog.java` 의 `sameNum…diffDen`·`measuredOn` 과 일치율 근거 javadoc, `{event}/judge/ClusteringJudge.java` 의 `agreementOf` 와 그 javadoc 언급을 지운다. `Agreement` 레코드·모델·단가·`estimateUsd` 는 남긴다. 컴파일 오류가 나는 사용처를 모두 고친다.
- [x] T014 [US1] `{event-test}/service/JudgeAgreementServiceTest.java`(`@IntegrationTest`) — V146 시드 분포(v2 225행: SAME 134 · DIFF 88 · UNSURE 3, v1 합계 행·쌍별 0행)와, 테스트 트랜잭션에 넣은 작은 측정(알림·라벨 몇 쌍)으로 `latest` 값과 라벨 0행일 때 empty 를 확인한다.
- [x] T015 [P] [US1] `frontend/src/api/clusteringAdminApi.ts` 의 `ClusteringJudgeVersion` 에 `recomputable`, `unmeasuredPairs` 를 더하고, `frontend/src/app/admin/clustering/_sections.tsx` 의 `JudgeVersionsSection` 에 "합계만(라벨 변경 미반영)"·"측정에 없는 쌍 N" 표시를 넣는다(관리자 대시보드 프론트는 TDD 생략 결정 범위).

**체크포인트**: quickstart 1~3절을 돌려 SC-001·SC-002 를 확인한다. 변경이 100줄 이상이면 `code-reviewer` 에 diff 와 줄 범위를 넣어 검토받는다.

---

## Phase 4: 사용자 스토리 2 — 라벨을 고치면 판사를 다시 부르지 않고 일치율이 바뀐다 (P2)

**목표**: 관리자 화면에서 라벨을 고치면 다음 조회부터 일치율이 바뀐다. 계산이 이미 현재 라벨 기준이라 새 코드는 거의 없고, 확인이 중심이다.

**독립 테스트**: quickstart 4절(라벨 한 쌍 SAME→DIFF → v2 126/140 · 74/82, 판사 호출 0번).

- [x] T016 [US2] `frontend/src/lib/mutations/useUpdateEvalLabel.ts` 의 `onSuccess` 가 `["admin","clustering","eval-labels"]` 만 무효화한다(2026-11-26 확인). `["admin","clustering","judge-versions"]` 도 무효화해, 같은 탭에서 라벨을 고친 뒤 판사 버전 이력을 열면 옛 값이 남지 않게 한다.
- [ ] T017 [US2] 라벨 있는 로컬 사본에서 quickstart 4절을 돌려 SC-003 을 확인하고 라벨을 원래대로 되돌린다.

---

## Phase 5: 사용자 스토리 3 — 새 판사 버전을 재면 결과가 DB 에 쌓인다 (P3)

**목표**: `--eval.judge-agreement` 가 마크다운 대신 DB 에 측정을 저장한다.

**독립 테스트**: 판사를 가짜로 바꾼 통합 테스트로 측정 1행 + 쌍별 행(`origin='CLI'`)이 저장되는지 확인한다.

- [x] T018 [US3] `{event}/service/JudgeAgreementService.java` 에 `saveRun(version, model, dataset, verdicts, tokens, usd)` 를 더한다 — 측정 1행 + 쌍별 행을 한 트랜잭션으로 저장하고 id 를 돌려준다.
- [x] T019 [US3] `{event}/evaluation/JudgeAgreementRunner.java` 가 판정을 모두 마친 뒤 `saveRun` 으로 저장하고 콘솔에 대상 쌍 수·교차표·일치율(분자/분모)·UNSURE/형식 실패/호출 실패 수·토큰·추정 비용·측정 id 를 로그로 남기게 한다. 반환값은 리포트 문자열이 아니게 바꾼다. 비용은 `JudgeVersionCatalog.Entry.estimateUsd` 로 센다.
- [x] T020 [US3] `{event}/evaluation/JudgeAgreementReport.java` 를 삭제하고, 남는 사용처가 콘솔 요약뿐이면 `{event}/evaluation/JudgeAgreementCounter.java` 정리 여부를 판단한다(research R7). 관련 테스트 `{event-test}/evaluation/JudgeAgreementCounterTest.java` 는 남기는 코드에 맞춘다.
- [x] T021 [US3] `{event}/evaluation/EvaluationRunTool.java` — `--eval.judge-agreement` 와 `--eval.report` 또는 `--eval.pair-labels-as-of` 를 함께 주면 판사를 부르기 전에 오류로 끝낸다(contracts 3절). 측정의 `dataset` 은 `--eval.dataset` 값이다.
- [x] T022 [US3] 판사를 가짜(`ClusteringJudge` mock)로 바꾼 통합 테스트로 저장 흐름을 확인한다 — `{event-test}/evaluation/JudgeAgreementRunnerTest.java`. 판사 실제 호출 없음.

---

## Phase 6: 마무리

- [x] T023 [P] 문서: `specs/006-clustering-evaluation/plan.md`(일치율 출처·측정 도구 출력), `specs/008-clustering-admin-dashboard/spec.md` FR-010 과 `contracts/admin-clustering-api.md` 의 `GET /judge-versions`(새 필드, 출처), `CLAUDE.md` 일일 채점 문장(일치율은 DB 측정 기록), `backend/evaluation/README.md` 의 판사 일치율 측정 절(리포트 없음, DB 저장, 운영 반영은 시드)을 고친다.
- [ ] T024 전체 변경을 `code-reviewer` 로 한 번 검토받는다(T008·Phase 3 에서 본 부분은 빼고 추가분만). 결과를 사용자에게 보고한다.
- [ ] T025 quickstart 1~5절을 다시 돌려 결과를 tasks.md 아래 "실행 기록"에 남긴다. 커밋·푸시·PR 은 사용자가 요청할 때 한다.
- [ ] T026 배포 안내: 사용자가 운영 DB 에 `backend/evaluation/datasets/pair_label_data_2026_11.sql` 을 넣은 뒤(272행 확인) 배포하도록 PR 본문에 적는다. 배포 다음 날 채점 기록·일치율 확인(quickstart 6절, SC-004)은 사용자가 한다.

---

## 의존성과 실행 순서

- T001 → T002 → T003 → T004·T005·T006(병렬) → T007 → **사용자 Red 확인** → T008.
- US1(T009~T015)은 T008 뒤. T009 는 T002·T003 뒤, T010 은 T006·T008 뒤, T011·T012 는 T010 뒤, T013 은 T011·T012 뒤, T014 는 T009·T010 뒤. T015 는 T012 와 병렬 가능(응답 형식만 맞추면 됨).
- US2(T016·T017)는 US1 뒤.
- US3(T018~T022)는 T010 뒤면 US1·US2 와 독립. T019 는 T018 뒤, T020·T021 은 T019 뒤, T022 는 T019·T021 뒤.
- 마무리(T023~T026)는 모든 스토리 뒤.

## 병렬 실행 예

```text
T004 JudgeAgreementRun 엔티티  |  T005 JudgeAgreementRunPair 엔티티  |  T006 리포지토리 두 개
T015 프론트 표시               |  T012 대시보드 응답(백엔드)          (응답 필드 이름을 contracts 1절로 고정한 뒤)
T023 문서 갱신                 |  T024 리뷰 준비
```

## 구현 전략

1. **MVP = US1**(T001~T015): 일치율이 DB 에서 나오고 상수가 사라진다. 여기까지만으로도 배포할 수 있다(배포 전 라벨 덤프 필수).
2. US2 는 US1 의 계산 방식에서 저절로 따라오므로 캐시 무효화와 확인만 한다.
3. US3 는 다음 판사 버전을 잴 때 필요하다. 지금 v3 계획이 없으므로 US1 과 같은 PR 에 넣되, 범위가 커지면 다음 PR 로 미룰 수 있다.

## 실행 기록

- T001(2026-11-26): PR #358 머지(a8fab027) 확인, 브랜치를 develop 으로 fast-forward.
- T002(2026-11-26): `disaster_alert_prod` 사본에서 리포트 24쌍(모두 현재 라벨과 사람 라벨까지 일치) + 라벨 272쌍으로 225행 추출. 분포 SAME 134 · DIFF 88 · UNSURE 3, 교차표 SAME→SAME 127 · SAME→DIFF 14 · SAME→UNSURE 3 · DIFF→DIFF 74 · DIFF→SAME 7 — 리포트와 같음. 결과는 scratchpad(`v2_rows.csv`)에만 두고 저장소에 스크립트를 남기지 않음.
- T003~T006(2026-11-26): V145(두 테이블, CHECK·UNIQUE·인덱스, 알림 외래 키 없음), 엔티티 `JudgeAgreementRun`(평가 도구용 팩터리 `measured` 만, 시드 측정은 SQL 로만 들어옴)·`JudgeAgreementRunPair`(id 정규화), 리포지토리 두 개, 기록용 판정 enum `judge/RecordedVerdict`(CALL_FAILURE 포함, 순수). `compileJava` 통과. 기존 `EvaluationLabelRepository.findByDatasetOrderByIdAsc` 를 그대로 쓴다.
- T007(2026-11-26): `tdd-developer` RED — 계산기 테스트 4개 + 뼈대, 커밋 c984e220(이 커밋 뒤 사용자가 "Red·Green 은 스테이징까지만"으로 규칙을 바꿈). 4개 모두 단언 실패 확인, 사용자 Red 확인.
- T008(2026-11-26): `tdd-developer` GREEN — 4개 통과, 스테이징만. 클래스 javadoc 에 "판정 고정 × 현재 라벨" 근거 추가(사용자 요청).
- T009~T015(2026-11-26): V146 시드(225행 고정 VALUES), `JudgeAgreementService.latest`, 일일 채점·대시보드 연결, 카탈로그 일치율·`agreementOf` 제거, 화면 "비고" 칸. 기존 테스트 3개(카탈로그·일일 채점·관리자 API)는 상수 대신 테스트용 합계 측정을 넣어 값이 정해지게 고침. 관련 테스트 24개 통과(계산기 4·서비스 3·카탈로그 2·판사 4·일일 채점 4·관리자 API 7).
- 실제 라벨 확인(2026-11-26): `disaster_alert_eval_v6`(272쌍 라벨) 에 V145·V146 적용(Hibernate validate 통과) → v2 교차표 127/14/3/74/7, 측정에 없는 쌍 0. quickstart 2절 통과.
- T016(2026-11-26): `useUpdateEvalLabel` 이 judge-versions 캐시도 무효화.
- 운영 라벨(2026-11-26, 사용자 승인 "진행해라"): 운영 DB 에 `pair_label_data_2026_11.sql` 실행 — `INSERT 0 272`, SAME 144 · DIFF 81 · UNSURE 47, 순서 무관 중복 없음(272/272). 사전 확인: 덤프 알림 544개(272쌍 × 2, 겹침 없음) 모두 운영에 존재, 기존 라벨 0행. 이로써 FR-013 의 "배포 전 라벨" 조건이 충족됐다(T026 의 라벨 덤프 단계는 완료).
- T018~T022(2026-11-26): `JudgeAgreementService.saveRun`(측정 1행 + 쌍별 행, 한 트랜잭션), 측정 실행기가 판정을 다 마친 뒤 저장하고 측정 id 반환·콘솔 요약(교차표·일치율·UNSURE/형식 실패/호출 실패·토큰·비용), 호출 실패는 CALL_FAILURE 로 저장, 판사 버전이 카탈로그에 없으면 호출 전 예외. `JudgeAgreementReport` 삭제, `JudgeAgreementCounter` 는 콘솔 요약용으로 남김. 평가 도구가 `--eval.report`·`--eval.pair-labels-as-of` 와의 동시 지정을 판사 호출 전에 거부. 테스트: `JudgeAgreementRunnerTest`(가짜 판사, 실제 빈 조립), `EvaluationRunToolJudgeAgreementTest`(순수 Mockito 2개). 이벤트 도메인 테스트는 기존 Red(`HybridCandidateRankerTest` 7개, 2026-10-11 뼈대)만 실패하고 나머지 177개 통과.
- T023(2026-11-26): 006 plan(버전·일치율 측정·구조), 006 quickstart(측정 명령·출력·확인 문구), 008 spec(예외 상황·FR-010), 008 plan(당시 설계임을 밝힘), 008 계약 4절(새 필드·출처, 014 계약 링크), CLAUDE.md(일일 채점 일치율 출처), `backend/evaluation/README.md`(판사 일치율 측정 절 신설)를 고쳤다.

